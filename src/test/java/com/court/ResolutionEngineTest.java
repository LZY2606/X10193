package com.court;

import com.court.fixtures.Ar;
import com.court.fixtures.Elf;
import com.court.resolution.LoadEntry;
import com.court.resolution.Policies;
import com.court.resolution.Provider;
import com.court.resolution.Reference;
import com.court.resolution.Resolution;
import com.court.resolution.ResolutionResult;
import com.court.resolution.ResolverEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionEngineTest {

    private static LoadEntry elf(String label, int order, byte[] data) {
        return new LoadEntry(order, label, LoadEntry.Kind.ELF, data);
    }

    private static LoadEntry ar(String label, int order, byte[] data) {
        return new LoadEntry(order, label, LoadEntry.Kind.ARCHIVE, data);
    }

    private Resolution find(ResolutionResult r, String source, String symbol) {
        return r.getResolutions().stream()
                .filter(x -> x.getReference().sourceFile.id().equals(source)
                        && x.getReference().name().equals(symbol))
                .findFirst().orElseThrow(() -> new AssertionError("no resolution for " + source + "#" + symbol));
    }

    @Test
    void strongBeatsWeakRegardlessOfLoadOrder() {
        byte[] weakDef = obj("a.o", b -> b.sym("fn").weak().defined(1, 0x10));
        byte[] strongDef = obj("b.o", b -> b.sym("fn").defined(1, 0x20));
        byte[] user = obj("u.o", b -> {
            b.sym("fn").defined(0, 0);
            b.rela("fn", 0x0, Elf.R_X86_64_64, 0L);
        });
        Policies p = Policies.defaults();
        ResolutionResult r1 = new ResolverEngine(p).adjudicate("plan", "v1",
                List.of(elf("u.o", 0, user), elf("a.o", 1, weakDef), elf("b.o", 2, strongDef)));
        assertEquals(Resolution.Status.RESOLVED, find(r1, "u.o", "fn").getStatus());
        assertEquals("b.o", find(r1, "u.o", "fn").getWinner().file.getSourcePath());

        ResolutionResult r2 = new ResolverEngine(p).adjudicate("plan", "v2",
                List.of(elf("u.o", 0, user), elf("b.o", 1, strongDef), elf("a.o", 2, weakDef)));
        assertEquals("b.o", find(r2, "u.o", "fn").getWinner().file.getSourcePath());
        // old result object must not have drifted
        assertEquals("b.o", find(r1, "u.o", "fn").getWinner().file.getSourcePath());
    }

    @Test
    void hiddenSymbolIsNotExportedToOtherObjects() {
        byte[] hid = obj("hid.o", b -> b.sym("secret").hidden().defined(1, 0x10));
        byte[] user = obj("u.o", b -> {
            b.sym("secret").defined(0, 0);
            b.rela("secret", 0, Elf.R_X86_64_64, 0L);
        });
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("hid.o", 0, hid), elf("u.o", 1, user)));
        Resolution res = find(r, "u.o", "secret");
        assertEquals(Resolution.Status.UNRESOLVED, res.getStatus());
        assertTrue(res.getRejections().stream().anyMatch(x -> x.reason().contains("HIDDEN")));
    }

    @Test
    void commonSymbolsCoalesceToLargest() {
        byte[] c1 = obj("c1.o", b -> b.sym("buf").common(64, 16));
        byte[] c2 = obj("c2.o", b -> b.sym("buf").common(128, 32));
        byte[] user = obj("u.o", b -> {
            b.sym("buf").defined(0, 0);
            b.rela("buf", 0, Elf.R_X86_64_64, 0L);
        });
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("c1.o", 0, c1), elf("c2.o", 1, c2), elf("u.o", 2, user)));
        Resolution res = find(r, "u.o", "buf");
        assertEquals(Resolution.Status.RESOLVED, res.getStatus());
        assertEquals("c2.o", res.getWinner().file.getSourcePath());
        assertEquals(128, res.getWinner().symbol.getSize());
    }

    interface ObjBuilder { void build(Elf e); }

    private static byte[] obj(String name, ObjBuilder fn) {
        Elf e = Elf.reloc();
        fn.build(e);
        return e.build();
    }
    @Test
    void duplicateStrongCandidatesRemainAmbiguous() {
        byte[] aa = obj("a.o", x -> x.sym("dup").defined(1, 0x10));
        byte[] bb = obj("b.o", x -> x.sym("dup").defined(1, 0x20));
        byte[] user = obj("u.o", x -> {
            x.sym("dup").defined(0, 0);
            x.rela("dup", 0, Elf.R_X86_64_64, 0L);
        });
        Policies p = Policies.defaults();
        ResolutionResult r = new ResolverEngine(p).adjudicate("plan", "v1",
                List.of(elf("a.o", 0, aa), elf("b.o", 1, bb), elf("u.o", 2, user)));
        Resolution res = find(r, "u.o", "dup");
        assertEquals(Resolution.Status.AMBIGUOUS, res.getStatus());
        assertEquals(2, res.getTiedWinners().size(), "two equal strong candidates must be retained");
        assertNull(res.getWinner());

        Policies order = Policies.defaults();
        order.tiePolicy = Policies.TiePolicy.LOAD_ORDER;
        ResolutionResult r2 = new ResolverEngine(order).adjudicate("plan", "v2",
                List.of(elf("a.o", 0, aa), elf("b.o", 1, bb), elf("u.o", 2, user)));
        Resolution res2 = find(r2, "u.o", "dup");
        assertEquals(Resolution.Status.RESOLVED, res2.getStatus());
        assertEquals("a.o", res2.getWinner().file.getSourcePath());
    }

    @Test
    void archiveMembersAreExtractedLazilyAndCyclicArchivesConverge() {
        // a.o references b; b.o references a; both archives export each entry.
        byte[] a = obj("a.o", x -> {
            x.sym("sym_a").defined(1, 0x10);
            x.sym("sym_b").defined(0, 0);
            x.rela("sym_b", 0, Elf.R_X86_64_64, 0L);
        });
        byte[] b = obj("b.o", x -> {
            x.sym("sym_b").defined(1, 0x20);
            x.sym("sym_a").defined(0, 0);
            x.rela("sym_a", 0, Elf.R_X86_64_64, 0L);
        });
        byte[] archive = new Ar()
                .member("a.o", a).exports("a.o", "sym_a")
                .member("b.o", b).exports("b.o", "sym_b")
                .build();
        byte[] starter = obj("start.o", e -> {
            e.sym("sym_a").defined(0, 0);
            e.rela("sym_a", 0, Elf.R_X86_64_64, 0L);
        });
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("start.o", 0, starter), ar("libcycle.a", 1, archive)));
        long extracted = r.getActiveFiles().stream()
                .filter(f -> f.getRole().name().equals("EXTRACTED_OBJECT")).count();
        assertEquals(2, extracted, "cyclic archive dependencies must pull both members");
        assertEquals(0, r.unresolvedCount());
    }

    @Test
    void unneededArchiveMemberIsNeverExtracted() {
        byte[] a = obj("used.o", x -> x.sym("used").defined(1, 0x10));
        byte[] b = obj("unused.o", x -> x.sym("unused").defined(1, 0x10));
        byte[] archive = new Ar()
                .member("used.o", a).exports("used.o", "used")
                .member("unused.o", b).exports("unused.o", "unused")
                .build();
        byte[] starter = obj("start.o", e -> {
            e.sym("used").defined(0, 0);
            e.rela("used", 0, Elf.R_X86_64_64, 0L);
        });
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("start.o", 0, starter), ar("libx.a", 1, archive)));
        assertTrue(r.getActiveFiles().stream().noneMatch(f -> "unused.o".equals(f.getMemberName())));
        assertTrue(r.getActiveFiles().stream().anyMatch(f -> "used.o".equals(f.getMemberName())));
    }

    @Test
    void comdatGroupsAreDeduplicatedBySignature() {
        Elf e1 = Elf.reloc().sectionName(3, ".text.dup");
        e1.sym("dup_fn").defined(3, 0);
        e1.comdat("dup_fn").member(3);
        Elf e2 = Elf.reloc().sectionName(3, ".text.dup");
        e2.sym("dup_fn").defined(3, 0);
        e2.comdat("dup_fn").member(3);
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("first.o", 0, e1.build()), elf("second.o", 1, e2.build())));
        long dedupEvents = r.getTimeline().stream()
                .filter(t -> t.kind == com.court.resolution.TraceEvent.Kind.COMDAT_DEDUPED).count();
        assertEquals(1, dedupEvents);
    }

    @Test
    void corruptObjectIsQuarantinedButSiblingsSurvive() {
        byte[] good = obj("good.o", b -> b.sym("ok").defined(1, 0x10));
        byte[] bad = new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0};
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("good.o", 0, good), elf("bad.o", 1, bad)));
        assertEquals(1, r.getQuarantined().size());
        assertEquals("bad.o", r.getQuarantined().get(0).label());
        assertEquals(1, r.getActiveFiles().size());
    }

    @Test
    void weakUndefinedReferenceDoesNotFailResolution() {
        byte[] user = obj("u.o", b -> {
            b.sym("optional").weak().defined(0, 0);
            b.rela("optional", 0, Elf.R_X86_64_64, 0L);
        });
        ResolutionResult r = new ResolverEngine(Policies.defaults()).adjudicate("p", "v1",
                List.of(elf("u.o", 0, user)));
        Resolution res = find(r, "u.o", "optional");
        assertEquals(Resolution.Status.WEAK_UNRESOLVED, res.getStatus());
        assertEquals(0, r.unresolvedCount());
    }
}


