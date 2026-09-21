package court.elf;

import court.fixture.ElfBuilder;
import court.fixture.GroupSpec;
import court.fixture.Rela;
import court.fixture.Sym;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixtureRoundtripTest {

    @Test
    void relocatableWithSymbolsAndRelaRoundtrips() {
        byte[] elf = ElfBuilder.rel()
                .text("main-body")
                .syms(
                        Sym.undef("foo").func(),
                        Sym.weakUndef("maybe"),
                        new Sym("main").defined(0).func().at(0x10).sized(8)
                )
                .relas(new Rela("foo", Rela.R_X86_64_64, 0x18, 0))
                .build();
        ElfObject obj = ElfParser.parse(elf, "u1", "main.o");
        assertEquals(ElfObject.ET_REL, obj.type);
        assertEquals("x86-64", obj.machineName);
        ElfSymbol foo = obj.symbols.stream().filter(s -> s.name.equals("foo")).findFirst().orElseThrow();
        assertTrue(foo.isUndef());
        assertEquals(ElfSymbol.STT_FUNC, foo.type);
        ElfSymbol maybe = obj.symbols.stream().filter(s -> s.name.equals("maybe")).findFirst().orElseThrow();
        assertEquals(ElfSymbol.STB_WEAK, maybe.bind);
        ElfSymbol main = obj.symbols.stream().filter(s -> s.name.equals("main")).findFirst().orElseThrow();
        assertEquals(0x10, main.value);
        assertEquals(1, obj.relocs.size());
        assertEquals("R_X86_64_64", obj.relocs.get(0).typeName);
    }

    @Test
    void sharedObjectVersionsAndNeededRoundtrip() {
        byte[] so = ElfBuilder.so()
                .soname("libfoo.so.1")
                .need("libc.so.6")
                .verdef(new ElfBuilder.Verdef("LIBFOO_1.0", 1, true, List.of()))
                .verdef(new ElfBuilder.Verdef("LIBFOO_1.1", 2, false, List.of("LIBFOO_1.0")))
                .syms(
                        new Sym("bar").defined(0).func().at(0x20).version(2),
                        new Sym("old").defined(0).func().nonDefaultVersion(3)
                )
                .verdef(new ElfBuilder.Verdef("LIBFOO_PRIVATE", 3, false, List.of()))
                .build();
        ElfObject obj = ElfParser.parse(so, "u2", "libfoo.so.1");
        assertEquals(List.of("libc.so.6"), obj.needed);
        assertEquals("libfoo.so.1", obj.soname);
        VersionNode v11 = obj.versionsByIndex.get(2);
        assertEquals("LIBFOO_1.1", v11.name);
        assertTrue(v11.aliases.contains("LIBFOO_1.0"));
        ElfSymbol bar = obj.symbols.stream().filter(s -> s.name.equals("bar")).findFirst().orElseThrow();
        assertEquals("LIBFOO_1.1", bar.version.name);
        ElfSymbol old = obj.symbols.stream().filter(s -> s.name.equals("old")).findFirst().orElseThrow();
        assertTrue(old.version.nonDefault);
        assertTrue(obj.relocs.isEmpty());
    }

    @Test
    void comdatGroupRoundtrips() {
        byte[] elf = ElfBuilder.rel()
                .text("x")
                .groups(new GroupSpec("_Z3foov", List.of("inline-body".getBytes())))
                .syms(new Sym("call").defined(0).func())
                .build();
        ElfObject obj = ElfParser.parse(elf, "u3", "a.o");
        assertEquals(1, obj.groups.size());
        ComdatGroup g = obj.groups.get(0);
        assertEquals("_Z3foov", g.signature);
        assertTrue(g.comdat);
        assertEquals(1, g.memberSections.size());
    }
}
