package court.link;

import court.elf.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TieCopyCorruptTest {

    private final BinaryLoader loader = new BinaryLoader();
    private final LinkEngine engine = new LinkEngine(loader, new UnitExtractor());

    private LoadedFile f(int o, String n, byte[] d) { return loader.load(o, n, d); }
    private LinkReport run(LinkPolicy p, LoadedFile... fs) {
        return engine.resolve(new ArrayList<>(List.of(fs)), "t", p);
    }
    private Verdict v(LinkReport r, String n, String ver) {
        return r.verdicts.stream().filter(x -> x.name.equals(n)
                && (ver == null ? x.requestedVersion.isEmpty() : x.requestedVersion.equals(ver)))
                .findFirst().orElseThrow();
    }

    @Test
    void duplicateStrongCandidatesRemainAmbiguous() {
        byte[] a = ElfBuilder.rel().def("dup", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] b = ElfBuilder.rel().def("dup", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] u = ElfBuilder.rel().undef("dup", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "a.o", a), f(1, "b.o", b), f(2, "u.o", u));
        Verdict verdict = v(r, "dup", null);
        assertEquals("AMBIGUOUS", verdict.outcome);
        long ties = verdict.candidates.stream().filter(c -> c.status.equals("AMBIGUOUS")).count();
        assertEquals(2, ties, "equal candidates must not be silently ordered by filesystem traversal");
    }

    @Test
    void loadOrderPolicyResolvesTieButOnlyByExplicitOrder() {
        byte[] a = ElfBuilder.rel().def("dup", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] b = ElfBuilder.rel().def("dup", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] u = ElfBuilder.rel().undef("dup", Const.STB_GLOBAL).build();
        LinkPolicy p = LinkPolicy.x86_64();
        p.tiePolicy = "LOAD_ORDER";
        LinkReport r = run(p, f(0, "a.o", a), f(1, "b.o", b), f(2, "u.o", u));
        assertEquals("RESOLVED", v(r, "dup", null).outcome);
        assertEquals("a.o", v(r, "dup", null).winner.sourceFile);
        // reversed explicit order picks the other one
        LinkReport r2 = run(p, f(0, "b.o", b), f(1, "a.o", a), f(2, "u.o", u));
        assertEquals("b.o", v(r2, "dup", null).winner.sourceFile);
    }

    @Test
    void copyRelocationOnlyAcceptsSharedData() {
        byte[] so = ElfBuilder.dyn()
                .dynDef("global_state", Const.STB_GLOBAL, Const.STT_OBJECT, "", Const.STV_DEFAULT)
                .build();
        byte[] exec = ElfBuilder.dyn()
                .dynUndef("global_state", Const.STB_GLOBAL, null)
                .rela(0x3000, 8, 1, 0, ".bss")   // R_X86_64_COPY against dynsym index 1
                .build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "prog", exec), f(1, "libstate.so", so));
        Verdict verdict = v(r, "global_state", null);
        assertTrue(verdict.copyRelocation);
        assertEquals("RESOLVED", verdict.outcome);
        assertEquals("libstate.so", verdict.winner.sourceFile);
        assertTrue(verdict.affectedReferences.stream().anyMatch(ref -> ref.copyRelocation),
                "the COPY relocation must be recorded among affected references");
        assertEquals("R_X86_64_COPY",
                verdict.affectedReferences.stream().filter(rr -> rr.copyRelocation).findFirst().orElseThrow().relocType);
    }

    @Test
    void copyRelocationRejectsRelocatableDefinition() {
        byte[] objDef = ElfBuilder.rel().def("global_state", Const.STB_GLOBAL, Const.STT_OBJECT, 2).build();
        byte[] exec = ElfBuilder.dyn()
                .dynUndef("global_state", Const.STB_GLOBAL, null)
                .rela(0x3000, 8, 1, 0, ".bss")
                .build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "prog", exec), f(1, "obj.o", objDef));
        Verdict verdict = v(r, "global_state", null);
        assertTrue(verdict.candidates.stream().anyMatch(c -> c.reason != null
                && c.reason.contains("copy relocation")));
    }

    @Test
    void corruptObjectIsIsolatedAndSiblingsStillResolve() {
        byte[] good = ElfBuilder.rel().def("ok_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] bad = {0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 1, 2, 3};
        byte[] user = ElfBuilder.rel().undef("ok_fn", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "good.o", good), f(1, "bad.o", bad), f(2, "user.o", user));
        assertTrue(r.units.stream().anyMatch(u -> u.corrupt && u.displayName.equals("bad.o")));
        assertEquals("RESOLVED", v(r, "ok_fn", null).outcome);
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("bad.o")));
    }

    @Test
    void corruptArchiveMemberIsIsolatedOnly() {
        byte[] good = ElfBuilder.rel().def("g", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] bad = {0x7f, 'E', 'L', 'F', 2, 1};
        byte[] ar = new ArBuilder().add("g.o", good, "g").add("bad.o", bad).build();
        byte[] user = ElfBuilder.rel().undef("g", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), f(0, "user.o", user), f(1, "lib.a", ar));
        assertEquals("RESOLVED", v(r, "g", null).outcome);
    }
}
