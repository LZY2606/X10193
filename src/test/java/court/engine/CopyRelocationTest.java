package court.engine;

import court.fixture.ElfBuilder;
import court.fixture.Rela;
import court.fixture.Sym;
import court.model.Decision;
import court.model.Policy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopyRelocationTest {

    @Test
    void copyRelocationIsModeledAndNotSwallowedByOrdinalOverride() {
        // “可执行/主对象”对共享对象 OBJECT 符号发出 R_X86_64_COPY
        byte[] so = ElfBuilder.so().soname("libdata.so.1")
                .syms(new Sym("counter").defined(0).object(8).at(0x400))
                .build();
        byte[] exe = ElfBuilder.rel()
                .data(new byte[]{0, 0, 0, 0, 0, 0, 0, 0})
                .syms(Sym.undef("counter").object(8))
                .relas(new Rela("counter", Rela.R_X86_64_COPY, 0x20, 0))
                .build();

        Decision d = EngineTestSupport.create()
                .elf("exe", "prog.o", exe).elf("so", "libdata.so.1", so)
                .run(Policy.STATIC_LD);

        var r = d.refs.stream().filter(x -> x.key.symbol.equals("counter")).findFirst().orElseThrow();
        assertEquals("COPY", r.status, "COPY 必须显式呈现，不能被普通共享库覆盖规则吞掉");
        assertEquals("libdata.so.1", r.winner.unitName);
        assertTrue(r.affectedRelocs.stream().anyMatch(a -> a.copy));
        assertTrue(r.affectedRelocs.stream().anyMatch(a -> "R_X86_64_COPY".equals(a.type)));
        // 重定位总表也应可见
        long copyCount = d.refs.stream()
                .flatMap(x -> x.affectedRelocs.stream())
                .filter(a -> a.copy).count();
        assertEquals(1, copyCount);
    }
}
