package court.service;

import court.elf.ArBuilder;
import court.elf.Const;
import court.elf.ElfBuilder;
import court.link.LinkPolicy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds small, self-contained ELF/ar fixtures from code (no toolchain) so the court
 * is usable the moment it opens. Two load orders over the same inputs demonstrate
 * version drift and provider switching.
 */
@Component
public class DemoFixtures {

    public static final class Fixture {
        public final String fileName;
        public final byte[] data;
        public Fixture(String fileName, byte[] data) { this.fileName = fileName; this.data = data; }
    }

    // Shared library exporting a versioned function + a data object.
    public byte[] libFoo() {
        return ElfBuilder.dyn()
                .soname("libfoo.so")
                .dynDef("foo", Const.STB_GLOBAL, Const.STT_FUNC, "FOO_2.0", Const.STV_DEFAULT)
                .dynDef("foo", Const.STB_GLOBAL, Const.STT_FUNC, "FOO_1.0", Const.STV_DEFAULT)
                .dynDef("secret", Const.STB_GLOBAL, Const.STT_FUNC, "", Const.STV_HIDDEN)
                .dynDef("counter", Const.STB_GLOBAL, Const.STT_OBJECT, "FOO_2.0", Const.STV_DEFAULT)
                .verDef("FOO_2.0", List.of())
                .verDef("FOO_1.0", List.of())
                .build();
    }

    public byte[] libBar() {
        return ElfBuilder.dyn()
                .soname("libbar.so")
                .dynDef("foo", Const.STB_GLOBAL, Const.STT_FUNC, "", Const.STV_DEFAULT)
                .dynDef("helper", Const.STB_WEAK, Const.STT_FUNC, "", Const.STV_DEFAULT)
                .needed("libfoo.so")
                .dynUndef("foo", Const.STB_GLOBAL, "FOO_2.0")
                .verNeed("libfoo.so", "FOO_2.0", false)
                .build();
    }

    // relocatable main: strong main, strong foo, common buffer, weak helper, undefined dyn
    public byte[] mainObj() {
        return ElfBuilder.rel()
                .def("main", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .def("foo", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .common("buffer", 128)
                .undef("helper", Const.STB_WEAK)
                .rela(0x10, 4, 0, 0, ".text") // placeholder reloc (sym 0 ignored)
                .build();
    }

    public byte[] libApi() {
        return ElfBuilder.dyn()
                .soname("libapi.so")
                .dynDef("api", Const.STB_GLOBAL, Const.STT_FUNC, "API_9.0", Const.STV_DEFAULT)
                .verDef("API_9.0", List.of())
                .build();
    }

    public byte[] clientNeedsOld() {
        // A DYN client that references api@API_1.0 while the only library defines api@API_9.0.
        return ElfBuilder.dyn()
                .soname("libclient.so")
                .dynUndef("api", Const.STB_GLOBAL, "API_1.0")
                .verNeed("libapi.so", "API_1.0", false)
                .needed("libapi.so")
                .build();
    }

    public byte[] circularA() {
        return ElfBuilder.rel()
                .def("cycle_a", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .undef("cycle_b", Const.STB_GLOBAL)
                .build();
    }

    public byte[] circularB() {
        return ElfBuilder.rel()
                .def("cycle_b", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .undef("cycle_a", Const.STB_GLOBAL)
                .build();
    }

    public byte[] archive() {
        byte[] a = circularA();
        byte[] b = circularB();
        byte[] util = ElfBuilder.rel()
                .def("util_fn", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        return new ArBuilder()
                .add("cycle_a.o", a, "cycle_a")
                .add("cycle_b.o", b, "cycle_b")
                .add("util.o", util, "util_fn")
                .build();
    }

    public byte[] comdatA() {
        return ElfBuilder.rel()
                .def("inline_fn", Const.STB_WEAK, Const.STT_FUNC, 1)
                .comdat("inline_fn")
                .build();
    }

    public byte[] comdatB() {
        return ElfBuilder.rel()
                .def("inline_fn", Const.STB_WEAK, Const.STT_FUNC, 1)
                .comdat("inline_fn")
                .build();
    }

    public byte[] corruptObject() {
        // claims ELF but is truncated garbage
        return new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0};
    }

    /** Load order A: main + foo first, bar second (foo strong object intercepts). */
    public List<Fixture> orderA() {
        return List.of(
                new Fixture("main.o", mainObj()),
                new Fixture("comdat_a.o", comdatA()),
                new Fixture("comdat_b.o", comdatB()),
                new Fixture("libfoo.so", libFoo()),
                new Fixture("libbar.so", libBar()),
                new Fixture("libcycle.a", archive()),
                new Fixture("broken.o", corruptObject())
        );
    }

    /** Load order B: bar first, foo later; archives policy single-pass to show circular miss. */
    public List<Fixture> orderB() {
        return List.of(
                new Fixture("libbar.so", libBar()),
                new Fixture("libfoo.so", libFoo()),
                new Fixture("main.o", mainObj()),
                new Fixture("comdat_b.o", comdatB()),
                new Fixture("comdat_a.o", comdatA()),
                new Fixture("libcycle.a", archive())
        );
    }

    /** Unmet version scenario: client wants API_1.0 but library only defines API_9.0. */
    public List<Fixture> unmetVersion() {
        return List.of(
                new Fixture("client.o", clientNeedsOld()),
                new Fixture("libapi.so", libApi())
        );
    }
}
