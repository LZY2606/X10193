package com.symbolcourt.web;

import com.symbolcourt.model.*;
import com.symbolcourt.model.Enums.RelocKind;
import com.symbolcourt.resolve.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Flattens a LinkResult into JSON-serializable maps. */
public final class ViewDto {

    private ViewDto() {}

    public static Map<String, Object> result(LinkResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policy", Map.of("id", r.policy.id, "displayName", r.policy.displayName));
        m.put("summary", r.summary);
        m.put("warnings", r.warnings);
        m.put("errors", r.errors);
        m.put("dependencyOrder", r.dependencyOrder);
        m.put("extractionLog", r.extractionLog);
        m.put("isolatedCorrupt", r.isolatedCorrupt.stream().map(e ->
                Map.of("name", str(e.displayName), "error", str(e.error))).toList());

        List<Map<String, Object>> units = new ArrayList<>();
        for (InputUnit u : r.units) {
            Map<String, Object> um = unitMap(u);
            units.add(um);
        }
        m.put("units", units);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (Step s : r.steps) steps.add(stepMap(s));
        m.put("steps", steps);

        List<Map<String, Object>> resolutions = new ArrayList<>();
        for (SymbolResolution s : r.resolutions.values()) resolutions.add(resolutionMap(s));
        m.put("resolutions", resolutions);

        List<Map<String, Object>> relocs = new ArrayList<>();
        for (RelocationDecision d : r.relocations) relocs.add(relocMap(d));
        m.put("relocations", relocs);
        return m;
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static Map<String, Object> unitMap(InputUnit u) {
        Map<String, Object> um = new LinkedHashMap<>();
        um.put("id", u.id);
        um.put("label", u.label());
        um.put("type", u.type.name());
        um.put("order", u.order);
        um.put("extracted", u.extracted);
        if (u.elf != null) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("kind", u.elf.kind.name());
            em.put("machine", u.elf.machineValue);
            em.put("soname", u.elf.soname);
            em.put("interpreter", u.elf.interpreter);
            em.put("needed", u.elf.needed);
            em.put("corrupt", u.elf.corrupt);
            em.put("error", u.elf.error);
            em.put("sectionCount", u.elf.sections.size());
            em.put("symbolCount", u.elf.symbols.size());
            em.put("dynSymbolCount", u.elf.dynamicSymbols.size());
            em.put("relocCount", u.elf.relocations.size());
            List<Map<String, Object>> vds = new ArrayList<>();
            for (VersionDef vd : u.elf.versionDefs) {
                vds.add(Map.of("index", vd.index, "name", vd.name, "base", vd.base,
                        "aliases", vd.aliases));
            }
            em.put("versionDefs", vds);
            List<Map<String, Object>> vns = new ArrayList<>();
            for (VersionNeed vn : u.elf.versionNeeds) {
                Map<String, Object> vnm = new LinkedHashMap<>();
                vnm.put("file", vn.file);
                List<Map<String, Object>> es = new ArrayList<>();
                for (VersionNeed.Entry e : vn.entries) {
                    es.add(Map.of("index", e.index, "name", e.name, "weak", e.weak));
                }
                vnm.put("entries", es);
                vns.add(vnm);
            }
            em.put("versionNeeds", vns);
            List<Map<String, Object>> groups = new ArrayList<>();
            for (GroupInfo g : u.elf.groups) {
                List<String> secs = new ArrayList<>();
                for (int idx : g.sectionIndices) {
                    ElfSection s = u.elf.sectionAt(idx);
                    secs.add(s != null ? s.name : ("section#" + idx));
                }
                groups.add(Map.of("signature", str(g.signature), "comdat", g.comdat,
                        "sections", secs));
            }
            em.put("groups", groups);
            List<Map<String, Object>> symbols = new ArrayList<>();
            addSymbols(symbols, u.elf.symbols, false);
            addSymbols(symbols, u.elf.dynamicSymbols, true);
            em.put("symbols", symbols);
            List<Map<String, Object>> rels = new ArrayList<>();
            for (ElfRelocation r : u.elf.relocations) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("section", r.targetSectionName);
                rm.put("offset", r.offset);
                rm.put("type", r.typeName);
                rm.put("symbol", r.symbolName);
                rm.put("dynamic", r.dynamic);
                rm.put("addend", r.addend);
                rm.put("kind", r.kind.name());
                rels.add(rm);
            }
            em.put("relocations", rels);
            um.put("elf", em);
        } else if (u.archive != null) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("corrupt", u.archive.corrupt);
            am.put("error", u.archive.error);
            List<Map<String, Object>> ms = new ArrayList<>();
            for (ArchiveMember mem : u.archive.members) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("name", mem.name);
                mm.put("corrupt", mem.corrupt);
                mm.put("error", mem.error);
                mm.put("defined", definedNames(mem));
                ms.add(mm);
            }
            am.put("members", ms);
            um.put("archive", am);
        }
        return um;
    }

    private static List<String> definedNames(ArchiveMember mem) {
        List<String> names = new ArrayList<>();
        if (mem.elf == null) return names;
        for (ElfSymbol s : mem.elf.symbols) {
            if (s.isDefined() && s.binding != com.symbolcourt.model.Enums.Binding.LOCAL
                    && s.name != null && !s.name.isEmpty()) names.add(s.versionedName());
        }
        return names;
    }

    private static void addSymbols(List<Map<String, Object>> out, List<ElfSymbol> syms, boolean dyn) {
        for (ElfSymbol s : syms) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("name", s.name);
            sm.put("version", s.versionName);
            sm.put("versionDefault", s.versionDefault);
            sm.put("versionHidden", s.versionHidden);
            sm.put("rawVersionIndex", s.rawVersionIndex);
            sm.put("binding", s.binding.name());
            sm.put("type", s.type.name());
            sm.put("visibility", s.visibility.name());
            sm.put("defined", s.isDefined());
            sm.put("common", s.isCommon());
            sm.put("shndx", s.shndx);
            sm.put("value", s.value);
            sm.put("size", s.size);
            sm.put("section", s.sectionName);
            sm.put("dynamic", dyn);
            sm.put("neededFrom", s.neededFrom);
            out.add(sm);
        }
    }

    private static Map<String, Object> stepMap(Step s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", s.index);
        m.put("kind", s.kind.name());
        m.put("title", s.title);
        m.put("detail", str(s.detail));
        m.put("unit", str(s.unit));
        m.put("symbol", str(s.symbol));
        m.put("facts", s.facts);
        return m;
    }

    private static Map<String, Object> resolutionMap(SymbolResolution s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", s.key.toString());
        m.put("name", s.name);
        m.put("requestedVersion", s.requestedVersion);
        m.put("status", s.status.name());
        m.put("note", str(s.note));
        m.put("weakReference", s.weakReference);
        m.put("chosen", s.chosen == null ? null : s.chosen.describe());
        m.put("chosenUnit", s.chosen == null ? null : s.chosen.unitLabel());
        m.put("referencedBy", new ArrayList<>(s.referencedBy));
        List<Map<String, Object>> evs = new ArrayList<>();
        for (CandidateEvaluation e : s.evaluations) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("candidate", e.candidate.describe());
            ev.put("unit", e.candidate.unitLabel());
            ev.put("accepted", e.accepted);
            ev.put("rank", e.rank);
            ev.put("eliminationReason", str(e.eliminationReason));
            evs.add(ev);
        }
        m.put("candidates", evs);
        return m;
    }

    private static Map<String, Object> relocMap(RelocationDecision d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unit", d.unit);
        m.put("section", d.targetSection);
        m.put("offset", d.offset);
        m.put("type", d.typeName);
        m.put("kind", d.kind.name());
        m.put("symbol", d.symbolName);
        m.put("requestedVersion", d.requestedVersion);
        m.put("status", d.status);
        m.put("provider", d.provider);
        m.put("boundValue", d.boundValue);
        m.put("satisfied", d.satisfied);
        m.put("versionMatched", d.versionMatched);
        m.put("addend", d.addend);
        m.put("explanation", str(d.explanation));
        return m;
    }

    public static Map<String, Object> diff(SchemeDiff.Result d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("providerSwitchCount", d.providerSwitchCount);
        m.put("addressOnlyCount", d.addressOnlyCount);
        m.put("onlyInA", d.onlyInA);
        m.put("onlyInB", d.onlyInB);
        m.put("statusChanges", d.statusChanges);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (SchemeDiff.RefChange c : d.changes) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("symbol", c.symbol);
            cm.put("referencedFrom", c.referencedFrom);
            cm.put("providerBefore", c.providerBefore);
            cm.put("providerAfter", c.providerAfter);
            cm.put("providerChanged", c.providerChanged);
            cm.put("addressOnly", c.addressOnly);
            cm.put("note", c.note);
            changes.add(cm);
        }
        m.put("changes", changes);
        return m;
    }
}
