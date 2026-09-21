package court.engine;

import court.fixture.ArchiveBuilder;
import court.fixture.ElfBuilder;
import court.fixture.Sym;
import court.model.Decision;
import court.model.LoadUnit;
import court.model.Policy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveLazyTest {

    private byte[] objDefining(String name) {
        return ElfBuilder.rel().text(name).syms(new Sym(name).defined(0).func()).build();
    }

    @Test
    void onlyNeededArchiveMemberIsExtracted() {
        byte[] a = objDefining("need_fn");
        byte[] unused = objDefining("unused_fn");
        byte[] archive = new ArchiveBuilder().member("need.o", a).member("unused.o", unused).build();
        byte[] main = ElfBuilder.rel().text("main").syms(Sym.undef("need_fn")).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).archive("ar", "libx.a", archive)
                .run(Policy.STATIC_LD);

        LoadUnit need = d.loadOrder.stream()
                .filter(u -> u.name.contains("need.o")).findFirst().orElseThrow();
        LoadUnit unusedUnit = d.loadOrder.stream()
                .filter(u -> u.name.contains("unused.o")).findFirst().orElseThrow();
        assertTrue(need.extracted);
        assertFalse(unusedUnit.extracted);

        var r = d.refs.stream().filter(x -> x.key.symbol.equals("need_fn")).findFirst().orElseThrow();
        assertEquals("RESOLVED", r.status);
        assertEquals("libx.a(need.o)", r.winner.unitName);

        // 必须有抽取播放步骤
        assertTrue(d.steps.stream().anyMatch(s -> s.phase.equals("ARCHIVE_SCAN")
                && s.title.contains("libx.a(need.o)")));
    }

    @Test
    void unusedArchiveIsNotPulledAndWeakUndefDoesNotExtract() {
        byte[] weakProvider = objDefining("weak_fn");
        byte[] archive = new ArchiveBuilder().member("w.o", weakProvider).build();
        byte[] main = ElfBuilder.rel().text("main").syms(Sym.weakUndef("weak_fn")).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).archive("ar", "libw.a", archive)
                .run(Policy.STATIC_LD);

        LoadUnit w = d.loadOrder.stream().filter(u -> u.name.contains("w.o")).findFirst().orElseThrow();
        assertFalse(w.extracted, "弱未定义不应触发 archive 抽取");
        var r = d.refs.stream().filter(x -> x.key.symbol.equals("weak_fn")).findFirst().orElseThrow();
        assertEquals("WEAK_UNDEF_KEPT", r.status);
    }

    @Test
    void circularReferencesBetweenArchiveMembersConverge() {
        // 同一归档内 a_fn 与 b_fn 互相引用：main 需 a_fn；多轮定点扫描必须把 b.o 也抽出来。
        byte[] a = ElfBuilder.rel().text("a")
                .syms(new Sym("a_fn").defined(0).func(), Sym.undef("b_fn").func()).build();
        byte[] b = ElfBuilder.rel().text("b")
                .syms(new Sym("b_fn").defined(0).func(), Sym.undef("a_fn").func()).build();
        byte[] archive = new ArchiveBuilder().member("a.o", a).member("b.o", b).build();
        byte[] main = ElfBuilder.rel().text("main").syms(Sym.undef("a_fn").func()).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).archive("ar", "libc.a", archive)
                .run(Policy.STATIC_LD);

        LoadUnit ao = d.loadOrder.stream().filter(u -> u.name.contains("a.o")).findFirst().orElseThrow();
        LoadUnit bo = d.loadOrder.stream().filter(u -> u.name.contains("b.o")).findFirst().orElseThrow();
        assertTrue(ao.extracted);
        assertTrue(bo.extracted, "循环依赖需要第二轮扫描抽取 b.o");

        var ra = d.refs.stream().filter(x -> x.key.symbol.equals("a_fn")).findFirst().orElseThrow();
        var rb = d.refs.stream().filter(x -> x.key.symbol.equals("b_fn")).findFirst().orElseThrow();
        assertEquals("RESOLVED", ra.status);
        assertEquals("RESOLVED", rb.status);
    }

    @Test
    void corruptArchiveMemberIsIsolatedButOthersStillExtract() {
        byte[] good = objDefining("good_fn");
        byte[] bad = "this is definitely not an elf".getBytes();
        byte[] archive = new ArchiveBuilder().member("good.o", good).member("bad.o", bad).build();
        byte[] main = ElfBuilder.rel().text("main").syms(Sym.undef("good_fn")).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).archive("ar", "libm.a", archive)
                .run(Policy.STATIC_LD);

        assertTrue(d.badObjects.stream().anyMatch(b -> b.name.contains("bad.o")));
        var r = d.refs.stream().filter(x -> x.key.symbol.equals("good_fn")).findFirst().orElseThrow();
        assertEquals("RESOLVED", r.status);
    }
}
