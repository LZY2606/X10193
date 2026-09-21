package court.link;

import court.elf.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the arbitration engine using programmatically constructed ELF fixtures. */
class ArbitrationTest {

    private final BinaryLoader loader = new BinaryLoader();
    private final UnitExtractor extractor = new UnitExtractor();
    private final LinkEngine engine = new LinkEngine(loader, extractor);

    private LoadedFile elf(String name, byte[] data) {
        return loader.load(0, name, data);
    }
    private LoadedFile elf(int order, String name, byte[] data) {
        return loader.load(order, name, data);
    }
    private LinkReport run(LinkPolicy policy, LoadedFile... fs) {
        return engine.resolve(new ArrayList<>(List.of(fs)), "test", policy);
    }
    private Verdict verdict(LinkReport r, String name, String ver) {
        return r.verdicts.stream()
                .filter(v -> v.name.equals(name) && (ver == null ? v.requestedVersion.isEmpty()
                        : v.requestedVersion.equals(ver)))
                .findFirst().orElseThrow(() -> new AssertionError("no verdict " + name + "@" + ver
                        + " in " + r.verdicts.stream().map(x -> x.name).toList()));
    }

    @Test
    void strongBeatsWeakAndCommon() {
        byte[] weak = ElfBuilder.rel()
                .def("fn", Const.STB_WEAK, Const.STT_FUNC, 1)
                .common("shared_buf", 32)
                .build();
        byte[] strong = ElfBuilder.rel()
                .def("fn", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .common("shared_buf", 16)
                .build();
        byte[] user = ElfBuilder.rel().undef("fn", Const.STB_GLOBAL)
                .undef("shared_buf", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), elf("weak.o", weak), elf("strong.o", strong), elf("user.o", user));
        Verdict fn = verdict(r, "fn", null);
        assertEquals("RESOLVED", fn.outcome);
        assertEquals("strong.o", fn.winner.sourceFile);
        assertTrue(fn.candidates.stream().anyMatch(c -> c.status.equals("ELIMINATED")
                && c.unitId.equals("weak.o")));
        // COMMON picks largest across same-named commons
        Verdict buf = verdict(r, "shared_buf", null);
        assertEquals("weak.o", buf.winner.sourceFile, "largest COMMON should win");
        assertEquals(32, buf.winner.size);
    }

    @Test
    void weakUndefinedResolvesToZero() {
        byte[] user = ElfBuilder.rel().undef("opt", Const.STB_WEAK).build();
        LinkReport r = run(LinkPolicy.x86_64(), elf("user.o", user));
        Verdict v = verdict(r, "opt", null);
        assertEquals("RESOLVED", v.outcome);
        assertNull(v.winner);
        assertTrue(v.summary.contains("弱引用"));
    }

    @Test
    void strongUndefinedIsUnresolved() {
        byte[] user = ElfBuilder.rel().undef("missing", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), elf("user.o", user));
        assertEquals("UNRESOLVED", verdict(r, "missing", null).outcome);
    }

    @Test
    void hiddenDefinitionDoesNotSatisfyOtherObject() {
        byte[] holder = ElfBuilder.rel()
                .def("secret", Const.STB_GLOBAL, Const.STT_FUNC, 1)
                .visibilityOf("secret", Const.STV_HIDDEN)
                .build();
        byte[] user = ElfBuilder.rel().undef("secret", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), elf("holder.o", holder), elf("user.o", user));
        Verdict v = verdict(r, "secret", null);
        assertEquals("UNRESOLVED", v.outcome);
        assertTrue(v.candidates.stream().anyMatch(c -> c.status.equals("ELIMINATED")
                && c.reason.contains("HIDDEN")));
    }

    @Test
    void protectedSymbolIsVisibleButNonPreemptive() {
        byte[] holder = ElfBuilder.dyn()
                .dynDef("guard", Const.STB_GLOBAL, Const.STT_FUNC, "", Const.STV_PROTECTED)
                .build();
        byte[] user = ElfBuilder.rel().undef("guard", Const.STB_GLOBAL).build();
        LinkReport r = run(LinkPolicy.x86_64(), elf("libguard.so", holder), elf("user.o", user));
        Verdict v = verdict(r, "guard", null);
        assertEquals("RESOLVED", v.outcome);
        assertEquals("libguard.so", v.winner.sourceFile);
    }
}
