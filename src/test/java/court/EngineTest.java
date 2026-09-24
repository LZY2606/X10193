package court;

import court.engine.*;
import court.fixture.ArBuilder;
import court.fixture.ElfBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static court.fixture.ElfBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    private LinkResult link(Policy p, byte[]... data) {
        java.util.List<InputEntry> in = new java.util.ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            in.add(new InputEntry("in" + i, "obj" + i + ".o", data[i]));
        }
        return new Linker().run(in, p);
    }

    private LinkResult linkNamed(Policy p, String[] names, byte[]... data) {
        java.util.List<InputEntry> in = new java.util.ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            in.add(new InputEntry("in" + i, names[i], data[i]));
        }
        return new Linker().run(in, p);
    }

    private Resolution res(LinkResult r, String name) {
        return r.resolutions.stream().filter(x -> x.name.equals(name)
                && x.version == null).findFirst().orElseThrow();
    }

    private Resolution resV(LinkResult r, String name, String ver) {
        return r.resolutions.stream().filter(x -> x.name.equals(name)
                && ver.equals(x.version)).findFirst().orElseThrow();
    }

    private byte[] relDef(String sym, int bind) {
        return new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal(sym, bind, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
    }

    private byte[] relUndef(String sym, int bind) {
        return new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym(sym, bind, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", sym, R_X86_64_64, 0, 0, false).build();
    }

    @Test
    void strongBeatsWeak() {
        byte[] strong = relDef("f", STB_GLOBAL);
        byte[] weak = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("f", STB_WEAK, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
        byte[] ref = relUndef("f", STB_GLOBAL);
        // weak first in order: strong must still win.
        LinkResult r = link(Policy.gnuDefault(), weak, strong, ref);
        Resolution x = res(r, "f");
        assertEquals("RESOLVED", x.status);
        assertTrue(x.winner.unitName.contains("obj1"), x.winner.unitName);
        assertEquals(ElfConstBind.GLOBAL, x.winner.bind);
    }

    @Test
    void undefinedWeakResolvesZeroWhenNoProvider() {
        byte[] ref = relUndef("maybe", STB_WEAK);
        LinkResult r = link(Policy.gnuDefault(), ref);
        Resolution x = res(r, "maybe");
        assertEquals("UNRESOLVED_WEAK", x.status);
    }

    @Test
    void archiveMemberLazilyExtractedOnlyWhenNeeded() {
        byte[] provider = relDef("helper", STB_GLOBAL);
        byte[] ar = new ArBuilder().add("helper.o", provider).build();
        byte[] needs = relUndef("helper", STB_GLOBAL);
        byte[] unrelated = relUndef("nope", STB_GLOBAL);
        LinkResult r = link(Policy.gnuStatic(), ar, needs);
        // helper member got extracted; an archive that is never needed stays dormant.
        byte[] ar2 = new ArBuilder().add("loner.o", relDef("loner", STB_GLOBAL)).build();
        LinkResult r2 = link(Policy.gnuStatic(), ar2, needs);
        assertTrue(r.resolutions.stream().anyMatch(x -> x.name.equals("helper")
                && "RESOLVED".equals(x.status)));
        Unit member = r.units.stream().filter(u -> u.memberName != null
                && u.memberName.equals("helper.o")).findFirst().orElseThrow();
        assertTrue(member.active);
        Unit loner = r2.units.stream().filter(u -> "loner.o".equals(u.memberName))
                .findFirst().orElseThrow();
        assertFalse(loner.active);
        assertTrue(r2.resolutions.stream().noneMatch(x -> x.name.equals("loner")));
        // archive extraction is replayed as steps
        assertTrue(r.steps.stream().anyMatch(s -> s.kind.equals("ARCHIVE_EXTRACT")));
    }

    @Test
    void archiveCycleExtractedByFixedPoint() {
        // main needs a; member a.o references b; member b.o defines a and b.
        byte[] main = relUndef("a", STB_GLOBAL);
        byte[] ao = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("a", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0, 4)
                .sym("b", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "b", R_X86_64_64, 4, 0, false)
                .build();
        byte[] bo = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("b", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
        byte[] ar = new ArBuilder().add("a.o", ao).add("b.o", bo).build();
        LinkResult r = link(Policy.gnuStatic(), main, ar);
        assertEquals("RESOLVED", res(r, "a").status);
        assertEquals("RESOLVED", res(r, "b").status);
        assertTrue(r.units.stream().filter(u -> "b.o".equals(u.memberName))
                .findFirst().orElseThrow().active);
    }

    @Test
    void hiddenSymbolIsNotExported() {
        byte[] lib = new ElfBuilder(ET_DYN).soname("libhid.so").progbits(".text", 8)
                .symVal("secret", STB_GLOBAL, STT_FUNC, STV_HIDDEN, ".text", 0x30, 4)
                .build();
        byte[] exe = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("secret", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "secret", R_X86_64_64, 0, 0, false).build();
        LinkResult r = link(Policy.gnuDefault(), exe, lib);
        Resolution x = res(r, "secret");
        assertNotEquals("RESOLVED", x.status);
    }

    @Test
    void versionAliasesAndNonDefaultVisibility() {
        // lib defines gnu@VERS_1.1 non-default and gnu@@VERS_1.0 default (alias).
        byte[] lib = new ElfBuilder(ET_DYN).soname("libg.so").progbits(".text", 16)
                .symVal("gnu", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x100, 4)
                .symVal("gnu2", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x110, 4)
                .versionDef("LIBG_1.0", 1, true, "VERS_1.0")
                .versionDef("VERS_1.1", 2, false)
                .symVersion("gnu", 2, true)   // non-default 1.1
                .symVersion("gnu2", 1, false) // default 1.0
                .build();

        // unversioned reference binds the default-version definition, not the hidden one
        byte[] plain = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("gnu", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "gnu", R_X86_64_64, 0, 0, false).build();
        LinkResult r = link(Policy.gnuDefault(), plain, lib);
        Resolution x = res(r, "gnu");
        assertEquals("UNRESOLVED", x.status,
                "无版本引用不能绑定 non-default；默认版本定义名字不同也不匹配");

        // unversioned name with a default version of the same name does bind
        byte[] lib2 = new ElfBuilder(ET_DYN).soname("libg2.so").progbits(".text", 16)
                .symVal("api", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x50, 4)
                .versionDef("API_2", 1, false)
                .symVersion("api", 1, false)
                .build();
        byte[] ref2 = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("api", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "api", R_X86_64_64, 0, 0, false).build();
        LinkResult r2 = link(Policy.gnuDefault(), ref2, lib2);
        assertEquals("RESOLVED", res(r2, "api").status);

        // versioned requirement exactly 1.1 selects the hidden-version definition
        byte[] refOld = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("gnu", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "gnu", R_X86_64_64, 0, 0, false).build();
        // static refs can't carry versions; exercise via a versioned DT_NEEDED-style DYN
        byte[] needer = new ElfBuilder(ET_DYN).soname("libh.so").needed("libg.so")
                .progbits(".text", 8)
                .sym("gnu", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .versionNeed("libg.so", "VERS_1.1", 2)
                .symVersion("gnu", 2, false)
                .reloc(".text", "gnu", R_X86_64_JUMP_SLOT, 0x200, 0, true)
                .build();
        LinkResult r3 = link(Policy.gnuDefault(), needer, lib);
        Resolution x3 = resV(r3, "gnu", "VERS_1.1");
        assertEquals("RESOLVED", x3.status);
        assertEquals("VERS_1.1", x3.winner.version);
    }

    @Test
    void unsatisfiedVersionRequirementErrors() {
        byte[] lib = new ElfBuilder(ET_DYN).soname("libg.so").progbits(".text", 8)
                .symVal("gnu", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x100, 4)
                .versionDef("VERS_2.0", 1, false)
                .symVersion("gnu", 1, false).build();
        byte[] needer = new ElfBuilder(ET_DYN).soname("libh.so").needed("libg.so")
                .progbits(".text", 8)
                .sym("gnu", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .versionNeed("libg.so", "VERS_9.9", 3)
                .symVersion("gnu", 3, false)
                .reloc(".text", "gnu", R_X86_64_JUMP_SLOT, 0x200, 0, true)
                .build();
        LinkResult r = link(Policy.gnuDefault(), needer, lib);
        Resolution x = resV(r, "gnu", "VERS_9.9");
        assertEquals("ERROR", x.status);
        assertTrue(x.detail.contains("未满足版本"));
    }

    @Test
    void commonSymbolsMergeAndYieldToStrong() {
        byte[] c1 = new ElfBuilder(ET_REL).progbits(".text", 0)
                .sym("buf", STB_GLOBAL, STT_OBJECT, STV_DEFAULT, SHN_COMMON).build();
        byte[] c2 = new ElfBuilder(ET_REL).progbits(".text", 0)
                .sym("buf", STB_GLOBAL, STT_OBJECT, STV_DEFAULT, SHN_COMMON).build();
        byte[] need = relUndef("buf", STB_GLOBAL);
        LinkResult r = link(Policy.gnuStatic(), c1, c2, need);
        Resolution x = res(r, "buf");
        assertEquals("RESOLVED", x.status);
        assertTrue(r.steps.stream().anyMatch(s -> s.kind.equals("COMMON_MERGE")));

        byte[] strong = new ElfBuilder(ET_REL).progbits(".data", 8)
                .symVal("buf", STB_GLOBAL, STT_OBJECT, STV_DEFAULT, ".data", 0, 8).build();
        LinkResult r2 = link(Policy.gnuStatic(), c1, strong, need);
        assertEquals("RESOLVED", res(r2, "buf").status);
        assertEquals(".data", res(r2, "buf").winner.sectionName);
    }

    @Test
    void comdatDeduplicatesAndRelocsFollowWinner() {
        byte[] a = new ElfBuilder(ET_REL)
                .progbits(".text.foo", 8).progbits(".data.foo", 8)
                .symVal("foo", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text.foo", 0, 4)
                .group("foo_sig", ".text.foo", ".data.foo").build();
        byte[] b = new ElfBuilder(ET_REL)
                .progbits(".text.foo", 8).progbits(".data.foo", 8)
                .symVal("foo", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text.foo", 0x90, 4)
                .group("foo_sig", ".text.foo", ".data.foo").build();
        byte[] need = relUndef("foo", STB_GLOBAL);
        LinkResult r = link(Policy.gnuStatic(), a, b, need);
        Resolution x = res(r, "foo");
        assertEquals("RESOLVED", x.status);
        Unit loser = r.units.get(1);
        assertFalse(loser.discards.isEmpty());
        assertTrue(r.steps.stream().anyMatch(s -> s.kind.equals("COMDAT_DISCARD")));
    }

    @Test
    void duplicateCandidatesStayAmbiguous() {
        byte[] w1 = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("w", STB_WEAK, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
        byte[] w2 = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("w", STB_WEAK, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
        LinkResult r = link(Policy.gnuStatic(), w1, w2);
        Resolution x = res(r, "w");
        assertEquals("AMBIGUOUS", x.status);
        // First-in-order policy resolves it deterministically instead.
        Policy first = Policy.gnuStatic();
        first.tieMode = Policy.TieMode.FIRST;
        LinkResult r2 = link(first, w1, w2);
        assertEquals("RESOLVED", res(r2, "w").status);
    }

    @Test
    void loadOrderSwapsWinnerAndDiffIsSemantic() {
        byte[] libA = new ElfBuilder(ET_DYN).soname("libA.so").progbits(".text", 8)
                .symVal("svc", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x111, 4).build();
        byte[] libB = new ElfBuilder(ET_DYN).soname("libB.so").progbits(".text", 8)
                .symVal("svc", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x222, 4).build();
        byte[] needer = new ElfBuilder(ET_DYN).soname("app.so").needed("libA.so")
                .needed("libB.so").progbits(".text", 8)
                .sym("svc", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "svc", R_X86_64_JUMP_SLOT, 0x10, 0, true).build();
        LinkResult ab = linkNamed(Policy.gnuDefault(),
                new String[]{"app.so", "libA.so", "libB.so"}, needer, libA, libB);
        LinkResult ba = linkNamed(Policy.gnuDefault(),
                new String[]{"app.so", "libB.so", "libA.so"}, needer, libB, libA);
        assertTrue(res(ab, "svc").winner.unitName.contains("libA.so"));
        assertTrue(res(ba, "svc").winner.unitName.contains("libB.so"));
        var diff = new SchemeDiff().compare(ab, ba);
        @SuppressWarnings("unchecked")
        java.util.List<SchemeDiff.Item> items =
                (java.util.List<SchemeDiff.Item>) diff.get("items");
        assertTrue(items.stream().anyMatch(i -> i.name.equals("svc")
                && i.kind.equals("PROVIDER_CHANGED")));
    }

    @Test
    void copyRelocationInterceptsNormally() {
        byte[] lib = new ElfBuilder(ET_DYN).soname("libv.so").progbits(".data", 8)
                .symVal("counter", STB_GLOBAL, STT_OBJECT, STV_DEFAULT, ".data", 0x40, 8)
                .build();
        // ET_EXEC-like dynamic unit carrying R_X86_64_COPY
        byte[] exe = new ElfBuilder(ET_DYN).progbits(".text", 8).progbits(".bss", 8)
                .sym("counter", STB_GLOBAL, STT_OBJECT, STV_DEFAULT, SHN_UNDEF)
                .reloc(".bss", "counter", R_X86_64_COPY, 0x60, 0, true).build();
        LinkResult r = linkNamed(Policy.gnuDefault(),
                new String[]{"exe.so", "libv.so"}, exe, lib);
        Resolution x = res(r, "counter");
        assertEquals("RESOLVED", x.status);
        assertNotNull(x.winner.copyDefinition);
        assertTrue(x.detail.contains("copy relocation"));
        assertTrue(x.candidates.stream().anyMatch(c -> c.def.unitName.contains("libv.so")));
    }

    @Test
    void protectedDataCopyRejectedUnderStrictPolicy() {
        byte[] lib = new ElfBuilder(ET_DYN).soname("libv.so").progbits(".data", 8)
                .symVal("counter", STB_GLOBAL, STT_OBJECT, STV_PROTECTED, ".data", 0x40, 8)
                .build();
        byte[] exe = new ElfBuilder(ET_DYN).progbits(".bss", 8)
                .sym("counter", STB_GLOBAL, STT_OBJECT, STV_DEFAULT, SHN_UNDEF)
                .reloc(".bss", "counter", R_X86_64_COPY, 0x60, 0, true).build();
        LinkResult r = link(Policy.strictProtected(), exe, lib);
        assertEquals("ERROR", res(r, "counter").status);
    }

    @Test
    void corruptObjectIsolatedOnlyItself() {
        byte[] good = relDef("g", STB_GLOBAL);
        byte[] bad = new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0};
        byte[] need = relUndef("g", STB_GLOBAL);
        LinkResult r = link(Policy.gnuStatic(), good, bad, need);
        assertTrue(r.corrupt.containsKey("obj1.o"));
        assertEquals("RESOLVED", res(r, "g").status);
    }

    @Test
    void malformedSectionsAndIndexesAreBounded() {
        // e_shoff points beyond file; parser must throw, not read garbage.
        byte[] b = new ElfBuilder(ET_REL).progbits(".text", 4)
                .symVal("z", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0, 1).build();
        b[40] = (byte) 0xff; b[41] = (byte) 0xff; b[42] = (byte) 0xff;
        b[43] = (byte) 0x7f; // huge shoff
        court.elf.ElfException ex = assertThrows(court.elf.ElfException.class,
                () -> new court.elf.ElfReader().parse(b));
        assertTrue(ex.getMessage().contains("bounds"));

        byte[] notElf = "hello world this is not an elf".getBytes();
        assertThrows(court.elf.ElfException.class,
                () -> new court.elf.ElfReader().parse(notElf));
    }

    @Test
    void localSymbolsAreNotCandidates() {
        byte[] withLocal = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("locfn", STB_LOCAL, STT_FUNC, STV_DEFAULT, ".text", 0, 4)
                .build();
        byte[] need = relUndef("locfn", STB_GLOBAL);
        LinkResult r = link(Policy.gnuStatic(), withLocal, need);
        assertEquals("UNRESOLVED", res(r, "locfn").status);
    }

    @Test
    void protectedReferenceBindsLocallyInsideSameDso() {
        byte[] lib = new ElfBuilder(ET_DYN).soname("libp.so")
                .progbits(".text", 8).progbits(".plt", 8)
                .symVal("pfunc", STB_GLOBAL, STT_FUNC, STV_PROTECTED, ".text", 0x77, 4)
                .reloc(".text", "pfunc", R_X86_64_JUMP_SLOT, 0x300, 0, true)
                .build();
        LinkResult r = link(Policy.gnuDefault(), lib);
        Resolution x = res(r, "pfunc");
        assertEquals("RESOLVED", x.status);
        assertTrue(x.winner.unitName.contains("libp.so"));
        assertEquals(0x77L, x.winner.value);
        assertTrue(r.steps.stream().anyMatch(s -> s.kind.equals("PROTECTED_LOCAL")));
    }

    @Test
    void weakReferenceDoesNotExtractArchiveByDefault() {
        byte[] provider = relDef("wsvc", STB_GLOBAL);
        byte[] ar = new ArBuilder().add("wsvc.o", provider).build();
        byte[] weakRef = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("wsvc", STB_WEAK, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "wsvc", R_X86_64_64, 0, 0, false).build();
        LinkResult r = link(Policy.gnuStatic(), ar, weakRef);
        Unit m = r.units.stream().filter(u -> "wsvc.o".equals(u.memberName))
                .findFirst().orElseThrow();
        assertFalse(m.active, "weak 未满足默认不应触发惰性抽取");

        Policy p = Policy.gnuStatic();
        p.weakExtractsArchives = true;
        LinkResult r2 = link(p, ar, weakRef);
        assertTrue(r2.units.stream().filter(u -> "wsvc.o".equals(u.memberName))
                .findFirst().orElseThrow().active);
    }
}
