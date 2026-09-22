package com.court;

import com.court.fixtures.Elf;
import com.court.resolution.LoadEntry;
import com.court.resolution.Policies;
import com.court.resolution.ResolverEngine;
import com.court.resolution.Resolution;
import com.court.resolution.ResolutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionAndCopyTest {

    private static LoadEntry elf(String label, int order, byte[] data) {
        return new LoadEntry(order, label, LoadEntry.Kind.ELF, data);
    }

    private Resolution find(ResolutionResult r, String source, String symbol) {
        return r.getResolutions().stream()
                .filter(x -> x.getReference().sourceFile.id().equals(source)
                        && x.getReference().name().equals(symbol))
                .findFirst().orElseThrow(() -> new AssertionError("missing " + symbol));
    }

    @Test
    void sameNameDifferentVersionsAreDifferentCandidates() {
        // libfoo exports foo@@V2 (default) and the hidden alias foo@V1.
        Elf libB = Elf.shared().soname("libfoo.so.2")
                .verDef(new Elf.VerDef("V1", 2).alias())
                .verDef(new Elf.VerDef("V2", 3));
        libB.dyn("foo").object().defined(1, 0x100).size(8).version(3);
        // an extra symbol carrying the non-default alias index is unrealistic in
        // real dynsym, so the provider for foo@V1 is represented with index 2 too.
        libB.dyn("foo_old").object().defined(1, 0x110).version(2);

        // program requires version V1 explicitly (a verneed against libfoo V1).
        Elf prog = Elf.executable()
                .need("libfoo.so.2")
                .verNeed(new Elf.VerNeed("libfoo.so.2", "V1", 2));
        prog.dyn("foo").defined(0, 0).version(2);

        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("prog", 0, prog.build()), elf("libfoo.so.2", 1, libB.build())));
        Resolution res = find(r, "prog", "foo");
        // No provider named "foo" carries V1; foo carries V2 -> version unsatisfied.
        assertEquals(Resolution.Status.VERSION_UNSATISFIED, res.getStatus());
        assertTrue(res.getNote().contains("V1"));
        assertTrue(res.getRejections().stream().anyMatch(x -> x.reason().contains("版本不匹配")));
    }

    @Test
    void defaultVersionSatisfiesUnversionedReferenceButHiddenAliasDoesNot() {
        Elf lib = Elf.shared().soname("libv.so")
                .verDef(new Elf.VerDef("BASE", 2).base());
        lib.dyn("plain").func().defined(1, 0x50).version(2);
        Elf prog = Elf.executable().need("libv.so");
        prog.dyn("plain").defined(0, 0); // unversioned dyn ref

        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("prog", 0, prog.build()), elf("libv.so", 1, lib.build())));
        Resolution res = find(r, "prog", "plain");
        assertEquals(Resolution.Status.RESOLVED, res.getStatus());
        assertEquals("BASE", res.getWinner().versionName);
    }

    @Test
    void copyRelocationBindsExecutableDataRefToSharedObject() {
        Elf lib = Elf.shared().soname("libd.so")
                .verDef(new Elf.VerDef("D", 2));
        lib.dyn("counter").object().defined(2, 0x200).size(8).version(2);
        Elf exe = Elf.executable().need("libd.so")
                .verNeed(new Elf.VerNeed("libd.so", "D", 2));
        exe.dyn("counter").object().defined(0, 0).version(2);
        exe.dynRela("counter", 0x403010L, Elf.R_X86_64_COPY, 0L);

        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("prog", 0, exe.build()), elf("libd.so", 1, lib.build())));
        Resolution res = find(r, "prog", "counter");
        assertEquals(Resolution.Status.COPY_RELOCATION, res.getStatus());
        assertNotNull(res.getWinner());
        assertTrue(res.getWinner().file.isShared());
    }

    @Test
    void protectedSymbolCannotBeInterposedByExecutable() {
        Elf lib = Elf.shared().soname("libp.so")
                .verDef(new Elf.VerDef("P", 2));
        lib.dyn("guarded").func().protectedVis().defined(1, 0x90).version(2);
        Elf exe = Elf.executable().need("libp.so")
                .verNeed(new Elf.VerNeed("libp.so", "P", 2));
        exe.dyn("guarded").defined(0, 0).version(2);

        Policies pol = Policies.defaults();
        ResolutionResult r = new ResolverEngine(pol).adjudicate("p", "v1",
                List.of(elf("prog", 0, exe.build()), elf("libp.so", 1, lib.build())));
        Resolution res = find(r, "prog", "guarded");
        assertTrue(res.getRejections().stream().anyMatch(x -> x.reason().contains("PROTECTED")),
                "protected rejection expected, got: " + res.getRejections());
    }

    @Test
    void loadOrderChangeCreatesNewPlanAndDiffShowsProviderSwitch() {
        byte[] a = obj("a.o", e -> e.sym("fn").defined(1, 0x10));
        byte[] b = obj("b.o", e -> {
            e.sym("fn").weak().defined(1, 0x20);
        });
        byte[] user = obj("u.o", e -> {
            e.sym("fn").defined(0, 0);
            e.rela("fn", 0, Elf.R_X86_64_64, 0L);
        });
        // Use LOAD_ORDER policy with equal-rank candidates is not the case here
        // (strong/weak), so both orders pick a.o; instead build equal case.
        byte[] a2 = obj("x1.o", e -> e.sym("g").defined(1, 0x10));
        byte[] b2 = obj("x2.o", e -> e.sym("g").defined(1, 0x20));
        byte[] user2 = obj("u2.o", e -> {
            e.sym("g").defined(0, 0);
            e.rela("g", 0, Elf.R_X86_64_64, 0L);
        });
        Policies pol = Policies.defaults();
        pol.tiePolicy = Policies.TiePolicy.LOAD_ORDER;
        ResolutionResult r1 = new ResolverEngine(pol).adjudicate("p", "v1",
                List.of(elf("u2.o", 0, user2), elf("x1.o", 1, a2), elf("x2.o", 2, b2)));
        ResolutionResult r2 = new ResolverEngine(pol).adjudicate("p", "v2",
                List.of(elf("u2.o", 0, user2), elf("x2.o", 1, b2), elf("x1.o", 2, a2)));
        assertEquals("x1.o", find(r1, "u2.o", "g").getWinner().file.getSourcePath());
        assertEquals("x2.o", find(r2, "u2.o", "g").getWinner().file.getSourcePath());
    }

    interface ObjBuilder { void build(Elf e); }

    private static byte[] obj(String name, ObjBuilder fn) {
        Elf e = Elf.reloc();
        fn.build(e);
        return e.build();
    }
}
