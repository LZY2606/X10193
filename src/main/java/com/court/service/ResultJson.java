package com.court.service;

import com.court.model.ElfFile;
import com.court.model.ElfSection;
import com.court.model.ElfSymbol;
import com.court.model.Relocation;
import com.court.model.SectionGroup;
import com.court.model.VersionDef;
import com.court.model.VersionReq;
import com.court.resolution.Provider;
import com.court.resolution.Quarantine;
import com.court.resolution.Reference;
import com.court.resolution.Resolution;
import com.court.resolution.TraceEvent;
import com.court.resolution.ResolutionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an internal ResolutionResult into stable JSON-friendly maps.
 * Explicit getters (rather than reflection) keep the on-wire contract stable
 * even if the internal model evolves.
 */
@Component
public class ResultJson {

    public Map<String, Object> toMap(ResolutionResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planName", r.getPlanName());
        out.put("planVersion", r.getPlanVersion());
        out.put("policies", policyMap(r));
        out.put("summary", Map.of(
                "activeFiles", r.getActiveFiles().size(),
                "quarantined", r.getQuarantined().size(),
                "resolutions", r.getResolutions().size(),
                "unresolved", r.unresolvedCount(),
                "ambiguous", r.ambiguousCount()));
        out.put("files", files(r));
        out.put("quarantined", quarantined(r));
        out.put("resolutions", resolutions(r));
        out.put("timeline", timeline(r));
        out.put("dependencyGraph", r.getDependencyGraph());
        out.put("versionGraph", r.getVersionGraph());
        out.put("symbolSnapshots", r.getSymbolTableSnapshots());
        return out;
    }

    private Map<String, Object> policyMap(ResolutionResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        var p = r.getPolicies();
        m.put("name", p.name);
        m.put("signature", p.signature());
        m.put("tiePolicy", p.tiePolicy.name());
        m.put("allowCopyRelocation", p.allowCopyRelocation);
        m.put("extractForWeak", p.extractForWeak);
        m.put("protectedNoInterposition", p.protectedNoInterposition);
        m.put("commonCoalesce", p.commonCoalesce);
        m.put("dedupeComdat", p.dedupeComdat);
        return m;
    }

    private List<Map<String, Object>> files(ResolutionResult r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ElfFile f : r.getActiveFiles()) out.add(fileMap(f));
        return out;
    }

    public Map<String, Object> fileMap(ElfFile f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id());
        m.put("source", f.getSourcePath());
        m.put("member", f.getMemberName());
        m.put("role", f.getRole().name());
        m.put("kind", f.isRelocatable() ? "ET_REL" : f.isExecutable() ? "ET_EXEC" : "ET_DYN");
        m.put("machine", f.getMachine());
        m.put("soname", f.getSoname());
        m.put("needed", f.getNeeded());
        m.put("sections", sectionList(f));
        m.put("symbols", symbolList(f));
        m.put("groups", f.getGroups().stream().map(this::groupMap).toList());
        m.put("versionDefs", f.getVersionDefs().stream().map(this::vdMap).toList());
        m.put("versionReqs", f.getVersionReqs().stream().map(this::vrMap).toList());
        m.put("relocations", f.getRelocations().stream().map(this::relMap).toList());
        return m;
    }

    private List<Map<String, Object>> sectionList(ElfFile f) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ElfSection s : f.getSections()) {
            if (s.type() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", s.index());
            m.put("name", s.name());
            m.put("type", s.type());
            m.put("address", s.addr());
            m.put("size", s.size());
            m.put("allocated", s.allocated());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> symbolList(ElfFile f) {
        List<Map<String, Object>> out = new ArrayList<>();
        appendSymbols(out, f, f.getStaticSymbols(), "symtab");
        appendSymbols(out, f, f.getDynamicSymbols(), "dynsym");
        return out;
    }

    private void appendSymbols(List<Map<String, Object>> out, ElfFile f,
                               List<ElfSymbol> syms, String table) {
        for (int i = 0; i < syms.size(); i++) {
            ElfSymbol s = syms.get(i);
            if (s.getName().isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("table", table);
            m.put("index", s.getIndex());
            m.put("name", s.getName());
            m.put("bind", s.bindName());
            m.put("type", s.getType());
            m.put("visibility", s.visibilityName());
            m.put("shndx", s.getShndx());
            m.put("undefined", s.isUndefined());
            m.put("common", s.isCommon());
            m.put("value", s.getValue());
            m.put("size", s.getSize());
            if ("dynsym".equals(table) && i < f.getDynVersym().size()) {
                var vv = com.court.resolution.VersionLookup.resolve(f, f.getDynVersym().get(i));
                if (vv != null) {
                    m.put("version", vv.name());
                    m.put("versionNonDefault", vv.nonDefault());
                    m.put("versionDefined", vv.defined());
                    m.put("versionIndex", f.getDynVersym().get(i));
                }
            }
            out.add(m);
        }
    }

    private Map<String, Object> groupMap(SectionGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signature", g.signature());
        m.put("comdat", g.comdat());
        m.put("sections", g.sectionIndices());
        return m;
    }

    private Map<String, Object> vdMap(VersionDef d) {
        return Map.of("index", d.index(), "name", d.name(),
                "nonDefault", d.nonDefault(), "base", d.base());
    }

    private Map<String, Object> vrMap(VersionReq v) {
        return Map.of("from", v.neededFrom(), "name", v.name(),
                "nonDefault", v.nonDefault());
    }

    private Map<String, Object> relMap(Relocation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("section", r.sectionIndex());
        m.put("offset", r.offset());
        m.put("symbolIndex", r.symbolIndex());
        m.put("symbol", r.symbolName());
        m.put("type", r.typeName());
        m.put("addend", r.addend());
        m.put("dynamic", r.dynamicRef());
        return m;
    }

    private List<Map<String, Object>> quarantined(ResolutionResult r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Quarantine q : r.getQuarantined()) {
            out.add(Map.of("label", q.label(), "reason", q.reason()));
        }
        return out;
    }

    private List<Map<String, Object>> resolutions(ResolutionResult r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Resolution res : r.getResolutions()) out.add(resolutionMap(res));
        return out;
    }

    private Map<String, Object> resolutionMap(Resolution res) {
        Reference ref = res.getReference();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", ref.sourceFile.id());
        m.put("symbol", ref.name());
        m.put("weak", ref.weakReference);
        m.put("requiredVersion", ref.requiredVersion);
        m.put("requiredVersionNonDefault", ref.nonDefaultVersion);
        m.put("status", res.getStatus().name());
        m.put("note", res.getNote());
        m.put("winner", res.getWinner() == null ? null : providerMap(res.getWinner()));
        m.put("tiedWinners", res.getTiedWinners().stream().map(this::providerMap).toList());
        m.put("candidates", res.getCandidates().stream().map(this::providerMap).toList());
        m.put("rejections", res.getRejections().stream()
                .map(x -> Map.of("provider", x.providerLabel(), "reason", x.reason())).toList());
        m.put("affectedRelocations", res.getAffectedRelocations());
        m.put("resolvedAddress", res.getResolvedAddress());
        return m;
    }

    private Map<String, Object> providerMap(Provider p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", p.fileLabel());
        m.put("symbol", p.symbolName());
        m.put("bind", p.symbol.bindName());
        m.put("visibility", p.symbol.visibilityName());
        m.put("common", p.symbol.isCommon());
        m.put("size", p.symbol.getSize());
        m.put("version", p.versionName);
        m.put("versionNonDefault", p.nonDefaultVersion);
        m.put("loadOrder", p.loadOrder);
        m.put("extractionRound", p.extractionRound);
        m.put("address", p.assignedAddress);
        return m;
    }

    private List<Map<String, Object>> timeline(ResolutionResult r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TraceEvent e : r.getTimeline()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", e.sequence);
            m.put("kind", e.kind.name());
            m.put("summary", e.summary);
            m.put("detail", e.detail);
            out.add(m);
        }
        return out;
    }
}
