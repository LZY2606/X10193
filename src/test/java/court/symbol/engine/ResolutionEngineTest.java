package court.symbol.engine;

import court.symbol.ar.Archive;
import court.symbol.elf.Elf;
import court.symbol.fixture.ElfFixture;
import court.symbol.model.Decision;
import court.symbol.model.InputObject;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.ResolutionReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionEngineTest {

    private static InputObject obj(int order, String label, byte[] content) {
        return new InputObject(order, label + "-" + order, label, "OBJECT", content);
    }

    private static InputObject ar(int order, String label, byte[] content) {
        return new InputObject(order, label + "-" + order, label, "ARCHIVE", content);
    }

    private static ResolutionReport run(PlatformPolicy policy, InputObject... inputs) {
        List<InputObject> list = List.of(inputs);
        return new Resolver(list, policy, "x86-64", "test", 1,
                System.currentTimeMillis()).resolve();
    }

    private static Decision forSymbol(ResolutionReport r, String name) {
        return r.decisions().stream()
                .filter(d -> d.reference().symbol().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("no decision for " + name));
    }

    @Test
    void strongBeatsWeakAcrossObjects() {
        byte[] user = ElfFixture.builder().relocatable()
                .addUndef("svc")
                .addRela(1, new ElfFixture.Rela(1, 4, 0, 0)).build();
        byte[] weakProvider = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("svc", 1).weak()).build();
        byte[] strongProvider = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("svc", 1)).build();

        ResolutionReport r = run(PlatformPolicy.staticDefault(),
                obj(0, "user.o", user), obj(1, "weak.o", weakProvider),
                obj(2, "strong.o", strongProvider));

        Decision d = forSymbol(r, "svc");
        assertEquals(Decision.RESOLVED, d.status());
        assertEquals("strong.o", d.winner().providerLabel());
        assertTrue(d.candidates().stream()
                .anyMatch(c -> c.candidate().providerLabel().equals("weak.o")
                        && c.state().equals("REJECTED")
                        && c.reason().contains("STRONG")));
    }

    @Test
    void weakUndefResolvesToNullWhenNoProvider() {
        byte[] user = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.undef("optional").weak())
                .addRela(1, new ElfFixture.Rela(1, 4, 0, 0)).build();
        ResolutionReport r = run(PlatformPolicy.staticDefault(), obj(0, "user.o", user));
        Decision d = forSymbol(r, "optional");
        assertEquals(Decision.RESOLVED, d.status());
        assertNull(d.winner());
    }

    @Test
    void commonSymbolsMergeLargest() {
        byte[] user = ElfFixture.builder().relocatable()
                .addUndef("pool")
                .addRela(1, new ElfFixture.Rela(1, 4, 0, 0)).build();
        byte[] c1 = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("pool", 0).common(8)).build();
        byte[] c2 = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("pool", 0).common(16)).build();
        ResolutionReport r = run(PlatformPolicy.staticDefault(),
                obj(0, "user.o", user), obj(1, "c1.o", c1), obj(2, "c2.o", c2));
        Decision d = forSymbol(r, "pool");
        assertEquals(Decision.COMMON_MERGE, d.status());
        assertTrue(d.rationale().contains("16"));
    }

    @Test
    void strongDefinitionBeatsCommon() {
        byte[] user = ElfFixture.builder().relocatable()
                .addUndef("state")
                .addRela(1, new ElfFixture.Rela(1, 4, 0, 0)).build();
        byte[] common = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("state", 0).common(8)).build();
        byte[] strong = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("state", 1).object(8)).build();
        ResolutionReport r = run(PlatformPolicy.staticDefault(),
                obj(0, "user.o", user), obj(1, "common.o", common),
                obj(2, "strong.o", strong));
        assertEquals("strong.o", forSymbol(r, "state").winner().providerLabel());
    }

    @Test
    void hiddenDefinitionRejectedAsCrossObjectProvider() {
        byte[] user = ElfFixture.builder().relocatable()
                .addUndef("stealth")
                .addRela(1, new ElfFixture.Rela(1, 4, 0, 0)).build();
        byte[] hidden = ElfFixture.builder().relocatable()
                .addSymbol(ElfFixture.Sym.def("stealth", 1).hidden()).build();
        ResolutionReport r = run(PlatformPolicy.staticDefault(),
                obj(0, "user.o", user), obj(1, "hidden.o", hidden));
        Decision d = forSymbol(r, "stealth");
        assertEquals(Decision.UNRESOLVED, d.status());
        assertTrue(d.candidates().get(0).reason().contains("HIDDEN"));
    }

    @Test
    void protectedDefinitionBindsSameObjectInDynamicModel() {
        // shared lib A defines protected svc and references it internally
        int undefIdx = ElfFixture.builder()
                .shared()
                .addSymbol(ElfFixture.Sym.undef("svc"));
        byte[] a = ElfFixture.builder().shared()
                .addSymbol(ElfFixture.Sym.def("svc", 1).protectedVis())
                .addRela(1, new ElfFixture.Rela(1, ElfFixture.R_X86_64_GLOB_DAT, 0, 0))
                .build();
        byte[] b = ElfFixture.builder().shared()
                .addSymbol(ElfFixture.Sym.def("svc", 1))
                .build();
        ResolutionReport r = run(PlatformPolicy.dynamicDefault(),
                obj(0, "libA.so", a), obj(1, "libB.so", b));
        Decision d = forSymbol(r, "svc");
        assertEquals("libA.so", d.winner().providerLabel());
        assertTrue(d.rationale().contains("PROTECTED"));
    }
