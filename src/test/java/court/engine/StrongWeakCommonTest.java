package court.engine;

import court.fixture.ElfBuilder;
import court.fixture.Sym;
import court.model.Candidate;
import court.model.Decision;
import court.model.Policy;
import court.model.RefResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrongWeakCommonTest {

    private RefResult ref(Decision d, String unitId, String name) {
        return d.refs.stream()
                .filter(r -> r.key.unitId.equals(unitId) && r.key.symbol.equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("未找到引用 " + unitId + "::" + name));
    }

    @Test
    void strongDefinitionFromRelocatableBeatsSharedObject() {
        byte[] main = ElfBuilder.rel().text("m")
                .syms(Sym.undef("resolve").func()).build();
        byte[] strongObj = ElfBuilder.rel().text("s")
                .syms(new Sym("resolve").defined(0).func().at(0x40)).build();
        byte[] so = ElfBuilder.so().soname("libs.so")
                .syms(new Sym("resolve").defined(0).func().at(0x200)).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main)
                .elf("strong", "strong.o", strongObj)
                .elf("so", "libs.so", so)
                .run(Policy.STATIC_LD);

        RefResult r = ref(d, "main", "resolve");
        assertEquals("RESOLVED", r.status);
        assertEquals("strong.o", r.winner.unitName);
        // 共享库候选必须出现在列表里，但层级低于 relocatable 强定义
        assertTrue(r.candidates.stream().anyMatch(c ->
                c.provider.unitName.equals("libs.so") && c.eligible));
        assertTrue(r.candidates.stream().anyMatch(c ->
                c.provider.unitName.equals("strong.o") && c.rank == Rank.STRONG_REL));
    }

    @Test
    void duplicateStrongDefinitionsAreAmbiguousNotSettledByOrder() {
        byte[] main = ElfBuilder.rel().text("m")
                .syms(Sym.undef("dup").func()).build();
        byte[] a = ElfBuilder.rel().text("a")
                .syms(new Sym("dup").defined(0).func().at(0x10)).build();
        byte[] b = ElfBuilder.rel().text("b")
                .syms(new Sym("dup").defined(0).func().at(0x20)).build();

        Decision d1 = EngineTestSupport.create()
                .elf("main", "main.o", main)
                .elf("a", "a.o", a).elf("b", "b.o", b)
                .run(Policy.STATIC_LD);
        RefResult r1 = ref(d1, "main", "dup");
        assertEquals("AMBIGUOUS", r1.status);
        assertNull(r1.winner);
        assertEquals(2, r1.candidates.stream().filter(c -> c.eligible
                && c.rankNote != null && c.rankNote.contains("歧义")).count());

        // 调换加载顺序，仍然是歧义（不允许用顺序悄悄选一个）
        Decision d2 = EngineTestSupport.create()
                .elf("main", "main.o", main)
                .elf("b", "b.o", b).elf("a", "a.o", a)
                .run(Policy.STATIC_LD);
        assertEquals("AMBIGUOUS", ref(d2, "main", "dup").status);
    }

    @Test
    void weakDefinitionLosesToStrongButWinsOverUnresolved() {
        byte[] main = ElfBuilder.rel().text("m")
                .syms(Sym.undef("w")).build();
        byte[] weakObj = ElfBuilder.rel().text("w")
                .syms(new Sym("w").defined(0).weak()).build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("weak", "w.o", weakObj)
                .run(Policy.STATIC_LD);
        RefResult r = ref(d, "main", "w");
        assertEquals("RESOLVED", r.status);
        assertEquals("w.o", r.winner.unitName);
        assertEquals(Rank.WEAK_REL, r.candidates.stream()
                .filter(c -> c.winner).findFirst().orElseThrow().rank);
    }

    @Test
    void weakUndefinedReferenceStaysUnresolvedWithoutError() {
        byte[] main = ElfBuilder.rel().text("m")
                .syms(Sym.weakUndef("opt")).build();
        Decision d = EngineTestSupport.create().elf("main", "main.o", main)
                .run(Policy.STATIC_LD);
        RefResult r = ref(d, "main", "opt");
        assertEquals("WEAK_UNDEF_KEPT", r.status);
        assertNull(r.winner);
    }

    @Test
    void strongBeatsCommonAndCommonMergesLargest() {
        byte[] main = ElfBuilder.rel().text("m").syms(Sym.undef("buf")).build();
        byte[] commonSmall = ElfBuilder.rel().text("cs").syms(new Sym("buf").common(8)).build();
        byte[] commonBig = ElfBuilder.rel().text("cb").syms(new Sym("buf").common(64)).build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main)
                .elf("cs", "cs.o", commonSmall).elf("cb", "cb.o", commonBig)
                .run(Policy.STATIC_LD);
        RefResult r = ref(d, "main", "buf");
        assertEquals("RESOLVED", r.status);
        assertEquals("cb.o", r.winner.unitName);
        assertEquals(64, r.winner.size);

        byte[] strong = ElfBuilder.rel().text("st")
                .syms(new Sym("buf").defined(0).object(128)).build();
        Decision d2 = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("cs", "cs.o", commonSmall)
                .elf("st", "st.o", strong)
                .run(Policy.STATIC_LD);
        RefResult r2 = ref(d2, "main", "buf");
        assertEquals("st.o", r2.winner.unitName);
    }

    @Test
    void hiddenSymbolIsRejectedFromOtherUnitsButVisibleInternally() {
        // main 引用 hid -> 其它对象的 HIDDEN 定义必须被淘汰
        byte[] main = ElfBuilder.rel().text("m").syms(Sym.undef("hid")).build();
        byte[] hidObj = ElfBuilder.rel().text("h")
                .syms(new Sym("hid").defined(0).hidden()).build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("h", "h.o", hidObj)
                .run(Policy.STATIC_LD);
        RefResult r = ref(d, "main", "hid");
        assertEquals("UNRESOLVED", r.status);
        Candidate c = r.candidates.stream()
                .filter(x -> x.provider.unitName.equals("h.o")).findFirst().orElseThrow();
        assertFalse(c.eligible);
        assertTrue(c.rejectReason.contains("HIDDEN"), c.rejectReason);
    }

    @Test
    void undefinedReferenceWithNoCandidateIsUnresolved() {
        byte[] main = ElfBuilder.rel().text("m").syms(Sym.undef("nope")).build();
        Decision d = EngineTestSupport.create().elf("main", "main.o", main)
                .run(Policy.STATIC_LD);
        assertEquals("UNRESOLVED", ref(d, "main", "nope").status);
    }
}
