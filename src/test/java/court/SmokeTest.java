package court;

import court.elf.ElfObject;
import court.elf.ElfReader;
import court.elf.Sym;
import court.fixture.ElfBuilder;
import org.junit.jupiter.api.Test;

import static court.fixture.ElfBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

class SmokeTest {

    @Test
    void parsesBuiltRel() throws Exception {
        byte[] a = new ElfBuilder(ET_REL)
                .progbits(".text", 16)
                .symVal("foo", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0x10, 8)
                .build();
        ElfObject elf = new ElfReader().parse(a);
        assertEquals("REL", elf.typeName);
        Sym foo = elf.staticSyms.stream().filter(s -> s.name.equals("foo")).findFirst().orElseThrow();
        assertEquals("GLOBAL", foo.bindName());
        assertEquals(".text", foo.section.name);
        assertEquals(0x10, foo.value);
    }
}
