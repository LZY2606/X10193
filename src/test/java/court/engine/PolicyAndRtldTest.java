package court.engine;

import court.fixture.ElfBuilder;
import court.fixture.Sym;
import court.model.Decision;
import court.model.Policy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolicyAndRtldTest {

    @Test
    void rtldResolvesInGlobalBfsOrderAndFirstWins() {
        byte[] main = ElfBuilder.so().soname("main")
                .need("liba.so").need("libb.so")
                .syms(Sym.undef("fn").func()).build();
        byte[] a = ElfBuilder.so().soname("liba.so")
                .syms(new Sym("fn").defined(0).func().at(0x10)).build();
        byte[] b = ElfBuilder.so().soname("libb.so")
                .syms(new Sym("fn").defined(0).func().at(0x20)).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main", main).elf("a", "liba.so", a).elf("b", "libb.so", b)
                .run(Policy.RTLD_GLOBAL);
        var r = d.refs.stream().filter(x -> x.key.symbol.equals("fn")).findFirst().orElseThrow();
        assertEquals("RESOLVED", r.status);
        assertEquals("liba.so", r.winner.unitName);
        // 调换顺序 => 不同结果（确定性，顺序来自显式加载，不来自目录遍历）
        Decision d2 = EngineTestSupport.create()
                .elf("main", "main", main).elf("b", "libb.so", b).elf("a", "liba.so", a)
                .run(Policy.RTLD_GLOBAL);
        assertEquals("libb.so", d2.refs.get(0).winner.unitName);
    }

    @Test
    void protectedDefinitionIsVisibleButNonInterposable() {
        byte[] p = ElfBuilder.so().soname("libp.so")
                .syms(new Sym("helper").defined(0).func().at(0x30).protectedVis())
                .build();
        byte[] q = ElfBuilder.so().soname("libq.so")
                .syms(new Sym("helper").defined(0).func().at(0x99))
                .build();
        byte[] user = ElfBuilder.so().soname("libu.so").need("libp.so")
                .syms(Sym.undef("helper").func()).build();

        Decision d = EngineTestSupport.create()
                .elf("u", "libu.so", user).elf("p", "libp.so", p).elf("q", "libq.so", q)
                .run(Policy.RTLD_GLOBAL);
        var r = d.refs.stream().filter(x -> x.key.symbol.equals("helper")).findFirst().orElseThrow();
        assertEquals("RESOLVED", r.status);
        assertEquals("libp.so", r.winner.unitName);
        assertEquals("PROTECTED", r.winner.visibility);
        var candQ = r.candidates.stream()
                .filter(c -> c.provider.unitName.equals("libq.so")).findFirst().orElseThrow();
        assertTrue(candQ.eligible, "PROTECTED 对其它单元仍是合法（可绑定）定义，只是不可被插入");
    }

    @Test
    void missingNeededDependencyIsWarnedButIsolatesOnlyThatEdge() {
        byte[] main = ElfBuilder.so().soname("main").need("libghost.so")
                .syms(Sym.undef("ghost_fn").func()).build();
        byte[] present = ElfBuilder.so().soname("libpresent.so")
                .syms(new Sym("local_fn").defined(0).func()).build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main", main).elf("present", "libpresent.so", present)
                .run(Policy.RTLD_GLOBAL);
        assertTrue(d.warnings.stream().anyMatch(w -> w.contains("libghost.so")));
        var r = d.refs.stream().filter(x -> x.key.symbol.equals("ghost_fn")).findFirst().orElseThrow();
        assertEquals("UNRESOLVED", r.status);
    }
}
