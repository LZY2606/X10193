package court.engine;

import court.fixture.ElfBuilder;
import court.fixture.Sym;
import court.model.Decision;
import court.model.Policy;
import court.model.RefDiff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemeComparatorTest {

    private RefDiff find(List<RefDiff> diffs, String symbol) {
        return diffs.stream().filter(d -> d.key.contains("::" + symbol)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少差异项 " + symbol));
    }

    @Test
    void distinguishesProviderChangeFromAddressOnlyChange() {
        byte[] main = ElfBuilder.rel().text("m").syms(Sym.undef("fn").func()).build();
        byte[] a10 = ElfBuilder.rel().text("a")
                .syms(new Sym("fn").defined(0).func().at(0x10)).build();
        byte[] a20 = ElfBuilder.rel().text("a")
                .syms(new Sym("fn").defined(0).func().at(0x20)).build();
        byte[] b = ElfBuilder.rel().text("b")
                .syms(new Sym("fn").defined(0).func().at(0x50)).build();

        Decision base = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("a", "a.o", a10)
                .run(Policy.STATIC_LD);
        Decision onlyAddress = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("a", "a.o", a20)
                .run(Policy.STATIC_LD);
        Decision switched = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("a", "a.o", a10).elf("b", "b.o", b)
                // 注意：两个强定义会歧义；这里让 a 变弱，从而 b 胜出
                .run(Policy.STATIC_LD);

        // 地址变化：同一 a.o 的同一符号槽，仅 st_value 不同
        List<RefDiff> d1 = SchemeComparator.compare(base, onlyAddress);
        assertEquals("ADDRESS_ONLY", find(d1, "fn").category);

        // 上面 switched 会歧义；改为 a 弱、b 强来制造真正的换提供者
        byte[] weakA = ElfBuilder.rel().text("a")
                .syms(new Sym("fn").defined(0).weak().at(0x10)).build();
        Decision weakBase = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("a", "a.o", weakA)
                .run(Policy.STATIC_LD);
        Decision strongSwitched = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("a", "a.o", weakA).elf("b", "b.o", b)
                .run(Policy.STATIC_LD);
        List<RefDiff> d2 = SchemeComparator.compare(weakBase, strongSwitched);
        RefDiff change = find(d2, "fn");
        assertEquals("PROVIDER_CHANGED", change.category);
        assertTrue(change.toProvider.contains("b.o"));
    }

    @Test
    void detectsStatusChangesAndNewOrGoneReferences() {
        byte[] main = ElfBuilder.rel().text("m").syms(Sym.undef("zzz")).build();
        Decision unresolved = EngineTestSupport.create()
                .elf("main", "main.o", main).run(Policy.STATIC_LD);
        byte[] provider = ElfBuilder.rel().text("p")
                .syms(new Sym("zzz").defined(0)).build();
        Decision resolved = EngineTestSupport.create()
                .elf("main", "main.o", main).elf("p", "p.o", provider)
                .run(Policy.STATIC_LD);
        List<RefDiff> diffs = SchemeComparator.compare(unresolved, resolved);
        RefDiff d = find(diffs, "zzz");
        assertEquals("STATUS_CHANGED", d.category);
    }
}
