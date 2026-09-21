package court.engine;

import court.fixture.ElfBuilder;
import court.fixture.GroupSpec;
import court.fixture.Sym;
import court.model.Decision;
import court.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComdatTest {

    @Test
    void duplicateComdatGroupsKeepFirstAndDropLaterSections() {
        byte[] a = ElfBuilder.rel()
                .groups(new GroupSpec("_Z3foov", List.of("body-A".getBytes())))
                .syms(new Sym("foo").defined(0).func().at(0x10))
                .build();
        byte[] b = ElfBuilder.rel()
                .groups(new GroupSpec("_Z3foov", List.of("body-B".getBytes())))
                .syms(new Sym("foo").defined(0).func().at(0x20))
                .build();
        byte[] main = ElfBuilder.rel().syms(Sym.undef("foo").func()).build();

        Decision d = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("a", "a.o", a).elf("b", "b.o", b)
                .run(Policy.STATIC_LD);

        assertEquals(1, d.comdat.size());
        assertEquals("a.o", d.comdat.get(0).instances.get(0).unitName);
        assertTrue(d.comdat.get(0).instances.get(0).kept);
        assertEquals("b.o", d.comdat.get(0).instances.get(1).unitName);
        assertFalse(d.comdat.get(0).instances.get(1).kept);

        // foo 不应因 COMDAT 去重而被当作“多个强定义歧义”
        var r = d.refs.stream().filter(x -> x.key.symbol.equals("foo")).findFirst().orElseThrow();
        assertEquals("RESOLVED", r.status);
        assertEquals("a.o", r.winner.unitName);
        // b.o 的候选因 COMDAT 被淘汰，而不是按 strong/weak 覆盖
        var dropped = r.candidates.stream()
                .filter(c -> c.provider.unitName.equals("b.o")).findFirst().orElseThrow();
        assertFalse(dropped.eligible);
        assertTrue(dropped.rejectReason.contains("COMDAT"));
    }

    @Test
    void reversingLoadOrderKeepsTheNewFirstComdatInstance() {
        byte[] a = ElfBuilder.rel()
                .groups(new GroupSpec("sig", List.of("A".getBytes())))
                .syms(new Sym("foo").defined(0).func()).build();
        byte[] b = ElfBuilder.rel()
                .groups(new GroupSpec("sig", List.of("B".getBytes())))
                .syms(new Sym("foo").defined(0).func()).build();
        Decision d = EngineTestSupport.create().elf("b", "b.o", b).elf("a", "a.o", a)
                .run(Policy.STATIC_LD);
        assertEquals("b.o", d.comdat.get(0).instances.get(0).unitName);
    }
}
