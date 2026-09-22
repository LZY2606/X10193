package com.example.symbolcourt.engine;

import com.example.symbolcourt.elf.ElfFile;
import com.example.symbolcourt.model.Candidate;
import com.example.symbolcourt.model.CopyRelocView;
import com.example.symbolcourt.model.GraphEdge;
import com.example.symbolcourt.model.GraphNode;
import com.example.symbolcourt.model.GroupOutcome;
import com.example.symbolcourt.model.Policy;
import com.example.symbolcourt.model.Reference;
import com.example.symbolcourt.model.RelocView;
import com.example.symbolcourt.model.StepEvent;
import com.example.symbolcourt.model.UnitInfo;
import com.example.symbolcourt.model.UnresolvedVersion;
import com.example.symbolcourt.model.Verdict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The arbitration court: deterministic, order-explicit symbol resolution without a system linker.
 * Strong/weak/common/local/visibility/version/COMDAT/copy-reloc semantics and lazy archive
 * extraction are all modelled explicitly; ties remain ambiguous instead of relying on
 * filesystem traversal order.
 */
public class CourtEngine {

    private static final int VERSYM_HIDDEN = 0x8000;

    private final Verdict v = new Verdict();
    private final List<Unit> units = new ArrayList<>();
    private final Map<String, Integer> unitOrdinalByName = new HashMap<>();
    private int stepSeq;

    private static final class Unit {
        int ordinal;
        ElfFile elf;
        String container; // archive name
        boolean archive;
        boolean active;
        boolean extracted;
        String parseError;
        final List<Unit> members = new ArrayList<>();
    }

    private static final class Entry {
        Unit unit;
        ElfFile.Sym sym;
        String version;
        boolean defaultVersion = true;
        boolean discarded;
    }

    private static final class RefKey {
        Unit unit;
        ElfFile.Sym sym;
        String version;
        boolean defaultVersion = true;
        String kind;
    }

    public Verdict arbitrate(List<ElfFile> inputs, Policy policy) {
        v.policy = policy;
        buildUnits(inputs);
        lazyArchives(policy);
        comdatDedup(policy);
        checkVersionNeeds();
        resolveReferences(policy);
        buildGraph();
        for (Unit u : units) {
            v.units.add(unitInfo(u));
        }
        return v;
    }

    private void buildUnits(List<ElfFile> inputs) {
        int ordinal = 0;
        for (ElfFile input : inputs) {
            if (input.archive) {
                Unit a = new Unit();
                a.ordinal = ordinal++;
                a.elf = input;
                a.archive = true;
                a.active = true;
                units.add(a);
                for (ElfFile m : input.members) {
                    Unit mu = new Unit();
                    mu.ordinal = ordinal++;
                    mu.elf = m;
                    mu.container = input.name;
                    mu.active = false;
                    mu.parseError = m.parseError;
                    a.members.add(mu);
                    units.add(mu);
                }
            } else {
                Unit u = new Unit();
                u.ordinal = ordinal++;
                u.elf = input;
                u.active = true;
                u.parseError = input.parseError;
                units.add(u);
            }
        }
        for (Unit u : units) {
            if (u.elf != null) {
                unitOrdinalByName.put(u.elf.name, u.ordinal);
            }
        }
    }

    private UnitInfo unitInfo(Unit u) {
        UnitInfo info = new UnitInfo();
        info.ordinal = u.ordinal;
        info.name = u.elf != null ? u.elf.name : ("unit-" + u.ordinal);
        info.kind = u.parseError != null ? "CORRUPT"
                : (u.archive ? "ARCHIVE" : (u.elf != null ? u.elf.kindName() : "UNKNOWN"));
        info.container = u.container;
        info.active = u.active;
        info.extracted = u.extracted;
        info.parseError = u.parseError;
        if (u.elf != null) {
            info.soname = u.elf.soname;
            info.needed.addAll(u.elf.needed);
            v.warnings.addAll(u.elf.warnings);
        }
        return info;
    }

    /** Classic lazy archive extraction, iterated to a fixed point for circular archives. */
    private void lazyArchives(Policy policy) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, List<Unit>> providerArchives = new LinkedHashMap<>();
            for (Unit a : units) {
                if (!a.archive) {
                    continue;
                }
                for (Unit m : a.members) {
                    if (m.active || m.parseError != null) {
                        continue;
                    }
                    if (definesNeededStrong(m)) {
                        providerArchives.computeIfAbsent(a.elf.name, k -> new ArrayList<>()).add(m);
                    }
                }
            }
            if (providerArchives.isEmpty()) {
                break;
            }
            // name -> archives whose first fitting member supplies it, for tie detection
            Map<String, List<String>> nameToArchives = new TreeMap<>();
            Map<String, List<Unit>> toExtract = new LinkedHashMap<>();
            for (Map.Entry<String, List<Unit>> e : providerArchives.entrySet()) {
                String archive = e.getKey();
                for (Unit m : e.getValue()) {
                    for (String name : strongDefinedNames(m)) {
                        if (isStrongUndef(name)) {
                            nameToArchives.computeIfAbsent(name, k -> new ArrayList<>());
                            if (!nameToArchives.get(name).contains(archive)) {
                                nameToArchives.get(name).add(archive);
                            }
                            toExtract.computeIfAbsent(archive, k -> new ArrayList<>());
                            if (!toExtract.get(archive).contains(m)) {
                                toExtract.get(archive).add(m);
                            }
                        }
                    }
                }
            }
            List<String> ambiguousNames = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : nameToArchives.entrySet()) {
                if (e.getValue().size() > 1) {
                    ambiguousNames.add(e.getKey());
                }
            }
            for (List<Unit> ms : toExtract.values()) {
                ms.sort(Comparator.comparingInt(x -> x.ordinal));
                for (Unit m : ms) {
                    m.active = true;
                    m.extracted = true;
                    changed = true;
                    StepEvent st = new StepEvent();
                    st.seq = stepSeq++;
                    st.kind = ambiguousNames.isEmpty() ? "EXTRACT" : "EXTRACT_AMBIGUOUS";
                    st.archive = m.container;
                    st.member = m.elf.name;
                    st.signature = String.join(",", ambiguousNames);
                    st.title = "从 " + m.container + " 惰性抽取 " + m.elf.name;
                    st.detail = ambiguousNames.isEmpty()
                            ? "成员提供当前强未定义符号"
                            : "多个归档在同一加载阶段对 " + ambiguousNames + " 提供候选，按规则不使用文件系统次序，全部抽取并保留歧义";
                    st.definedSymbols.addAll(strongDefinedNames(m));
                    for (Unit u : units) {
                        if (u.active && !u.archive) {
                            st.activeUnits.add(u.elf.name);
                        }
                    }
                    v.steps.add(st);
                }
            }
        }
    }

    private boolean definesNeededStrong(Unit m) {
        for (String name : strongDefinedNames(m)) {
            if (isStrongUndef(name)) {
                return true;
            }
        }
        return false;
    }

    private List<String> strongDefinedNames(Unit m) {
        List<String> out = new ArrayList<>();
        for (ElfFile.Sym s : m.elf.symtab) {
            if (s.name == null || s.name.isEmpty()) {
                continue;
            }
            if (s.isDefined() && s.bind() == ElfFile.STB_GLOBAL) {
                out.add(s.name);
            }
        }
        return out;
    }

    private boolean isStrongUndef(String name) {
        for (Unit u : units) {
            if (!u.active || u.archive || u.parseError != null) {
                continue;
            }
            for (ElfFile.Sym s : u.elf.symtab) {
                if (s.isUndef() && s.bind() == ElfFile.STB_GLOBAL && name.equals(s.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** COMDAT section-group deduplication across active units. */
    private void comdatDedup(Policy policy) {
        Map<String, List<Unit>> bySignature = new TreeMap<>();
        for (Unit u : activeRealUnits()) {
            for (ElfFile.Group g : u.elf.groups) {
                if ((g.flags & 1) == 1 && !g.signature.isEmpty()) {
                    bySignature.computeIfAbsent(g.signature, k -> new ArrayList<>()).add(u);
                }
            }
        }
        for (Map.Entry<String, List<Unit>> e : bySignature.entrySet()) {
            List<Unit> us = e.getValue();
            if (us.size() < 2) {
                continue;
            }
            us.sort(Comparator.comparingInt(this::effectiveOrdinal));
            Unit keep = us.get(0);
            GroupOutcome go = new GroupOutcome();
            go.signature = e.getKey();
            go.keptIn = keep.elf.name;
            for (int i = 1; i < us.size(); i++) {
                Unit loser = us.get(i);
                for (ElfFile.Group g : loser.elf.groups) {
                    if (e.getKey().equals(g.signature)) {
                        markDiscarded(loser, g);
                    }
                }
                go.discarded.add(loser.elf.name);
            }
            v.groups.add(go);
            StepEvent st = new StepEvent();
            st.seq = stepSeq++;
            st.kind = "COMDAT";
            st.signature = e.getKey();
            st.member = keep.elf.name;
            st.title = "COMDAT 去重：" + e.getKey();
            st.detail = "保留 " + keep.elf.name + "，淘汰 " + go.discarded;
            v.steps.add(st);
        }
    }

    private void markDiscarded(Unit u, ElfFile.Group group) {
        for (int secIdx : group.memberSections) {
            if (secIdx < 0 || secIdx >= u.elf.sections.size()) {
                continue;
            }
            ElfFile.Section sec = u.elf.sections.get(secIdx);
            for (ElfFile.Sym s : u.elf.symtab) {
                if (s.shndx == secIdx) {
                    discardedSections.computeIfAbsent(u, k -> new java.util.HashSet<>()).add(sec);
                }
            }
        }
    }

    private final Map<Unit, java.util.Set<ElfFile.Section>> discardedSections = new HashMap<>();

    private boolean isDiscarded(Unit u, ElfFile.Sym s) {
        if (s.shndx <= 0 || s.shndx >= u.elf.sections.size()) {
            return false;
        }
        ElfFile.Section sec = u.elf.sections.get(s.shndx);
        java.util.Set<ElfFile.Section> ds = discardedSections.get(u);
        return ds != null && ds.contains(sec);
    }

    private int effectiveOrdinal(Unit u) {
        if (u.container != null) {
            for (Unit a : units) {
                if (a.archive && a.elf != null && u.container.equals(a.elf.name)) {
                    return a.ordinal;
                }
            }
        }
        return u.ordinal;
    }

    private List<Unit> activeRealUnits() {
        List<Unit> out = new ArrayList<>();
        for (Unit u : units) {
            if (u.active && !u.archive && u.parseError == null) {
                out.add(u);
            }
        }
        out.sort(Comparator.comparingInt(this::effectiveOrdinal));
        return out;
    }

    private String versionName(Unit u, int symIndex) {
        ElfFile e = u.elf;
        if (e.versym == null || symIndex >= e.versym.length) {
            return null;
        }
        int vi = e.versym[symIndex] & 0x7fff;
        if (vi == 0 || vi == 1) {
            return null;
        }
        for (ElfFile.VerDef vd : e.verDefs) {
            if (vd.ndx == vi) {
                return vd.name;
            }
        }
        for (ElfFile.VerNeed vn : e.verNeeds) {
            for (ElfFile.VerNeedAux aux : vn.aux) {
                if (aux.other == vi) {
                    return aux.name;
                }
            }
        }
        return null;
    }

    private boolean isDefaultVersion(Unit u, int symIndex) {
        if (u.elf.versym == null || symIndex >= u.elf.versym.length) {
            return true;
        }
        return (u.elf.versym[symIndex] & VERSYM_HIDDEN) == 0;
    }

    private final Map<Unit, List<Entry>> entryCache = new HashMap<>();

    private List<Entry> entries(Unit u) {
        return entryCache.computeIfAbsent(u, this::buildEntries);
    }

    private List<Entry> buildEntries(Unit u) {
        List<Entry> out = new ArrayList<>();
        for (ElfFile.Sym s : u.elf.symtab) {
            if (s.index == 0 || s.name == null || s.name.isEmpty() || s.bind() == ElfFile.STB_LOCAL) {
                continue;
            }
            if (s.type() == 3 /* STT_SECTION */) {
                continue;
            }
            if (s.isUndef()) {
                continue;
            }
            Entry e = new Entry();
            e.unit = u;
            e.sym = s;
            e.discarded = isDiscarded(u, s);
            out.add(e);
        }
        for (ElfFile.Sym s : u.elf.dynsym) {
            if (s.index == 0 || s.name == null || s.name.isEmpty() || s.bind() == ElfFile.STB_LOCAL) {
                continue;
            }
            if (s.type() == 3) {
                continue;
            }
            if (s.isUndef()) {
                continue;
            }
            Entry e = new Entry();
            e.unit = u;
            e.sym = s;
            e.version = versionName(u, s.index);
            e.defaultVersion = isDefaultVersion(u, s.index);
            e.discarded = isDiscarded(u, s);
            out.add(e);
        }
        return out;
    }

    private void checkVersionNeeds() {
        for (Unit u : activeRealUnits()) {
            for (ElfFile.VerNeed vn : u.elf.verNeeds) {
                Unit provider = findUnitBySonameOrFile(vn.file);
                for (ElfFile.VerNeedAux aux : vn.aux) {
                    UnresolvedVersion uv = new UnresolvedVersion();
                    uv.unit = u.elf.name;
                    uv.fromFile = vn.file;
                    uv.requiredVersion = aux.name;
                    if (provider == null) {
                        uv.reason = "依赖对象未加载: " + vn.file;
                        v.unresolvedVersions.add(uv);
                    } else if (!hasVerDef(provider, aux.name)) {
                        uv.reason = provider.elf.name + " 未定义版本 " + aux.name;
                        v.unresolvedVersions.add(uv);
                    }
                }
            }
        }
    }

    private Unit findUnitBySonameOrFile(String file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String base = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;
        for (Unit u : activeRealUnits()) {
            if (file.equals(u.elf.soname) || base.equals(u.elf.name)
                    || file.equals(u.elf.name) || base.equals(u.elf.soname)) {
                return u;
            }
        }
        return null;
    }

    private boolean hasVerDef(Unit u, String name) {
        for (ElfFile.VerDef vd : u.elf.verDefs) {
            if (name.equals(vd.name)) {
                return true;
            }
        }
        return false;
    }
