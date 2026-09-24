package com.symbolcourt.resolve;

import com.symbolcourt.model.*;
import com.symbolcourt.model.Enums.*;

import java.util.*;

/**
 * Global symbol state built during the left-to-right, repeated-pass link scan.
 * Candidates are partitioned by (name, version) key; the same name with a
 * different version is deliberately a different key.
 */
public class CandidateRegistry {

    public final PlatformPolicy policy;
    public final Map<SymbolKey, List<Candidate>> global = new LinkedHashMap<>();
    public final Map<String, List<Candidate>> byNameGlobal = new LinkedHashMap<>();
    public final Map<String, Map<SymbolKey, Candidate>> local = new LinkedHashMap<>();
    public final List<RefRecord> refs = new ArrayList<>();
    public final Map<String, String> comdatOwner = new LinkedHashMap<>();
    public final List<String> notes = new ArrayList<>();

    public CandidateRegistry(PlatformPolicy policy) {
        this.policy = policy;
    }

    /** Sections dropped by COMDAT de-duplication for this unit (indices). */
    private final Map<String, Set<Integer>> droppedSections = new HashMap<>();

    public Set<Integer> droppedSections(InputUnit u) {
        return droppedSections.getOrDefault(u.id, Set.of());
    }

    public boolean isActive(InputUnit u) {
        return u.type != InputUnit.UnitType.ARCHIVE || u.extracted;
    }

    public List<ElfSymbol> allSymbols(ParsedElf elf) {
        List<ElfSymbol> out = new ArrayList<>(elf.symbols);
        out.addAll(elf.dynamicSymbols);
        return out;
    }

    /** Register COMDAT ownership decisions for an active object. */
    public void registerGroups(InputUnit u, List<Step> steps) {
        ParsedElf elf = u.elf;
        if (elf == null) return;
        Set<Integer> dropped = new HashSet<>();
        for (GroupInfo g : elf.groups) {
            if (!g.comdat || g.signature == null) continue;
            String owner = comdatOwner.get(g.signature);
            if (owner == null) {
                comdatOwner.put(g.signature, u.id);
            } else if (!owner.equals(u.id)) {
                for (int idx : g.sectionIndices) {
                    dropped.add(idx);
                    ElfSection s = elf.sectionAt(idx);
                    Step st = new Step(steps.size(), Step.Kind.COMDAT_DISCARD,
                            "COMDAT 去重丢弃 section",
                            g.signature + " 已由 " + owner + " 保留");
                    st.unit = u.label();
                    st.facts.add("丢弃 " + (s != null ? s.name : ("section#" + idx)));
                    steps.add(st);
                }
            }
        }
        if (!dropped.isEmpty()) droppedSections.put(u.id, dropped);
    }

    public int currentScanPos;
    public int currentPass;

    public void addDefinitions(InputUnit u) {
        ParsedElf elf = u.elf;
        Set<Integer> dropped = droppedSections.getOrDefault(u.id, Set.of());
        boolean shared = elf.isShared();
        List<ElfSymbol> syms = new ArrayList<>(elf.symbols);
        syms.addAll(elf.dynamicSymbols);
        for (ElfSymbol s : syms) {
            if (s.name == null || s.name.isEmpty()) continue;
            if (!s.isDefined()) continue;
            if (s.shndx > 0 && s.shndx < 0xff00 && dropped.contains(s.shndx)) continue;
            Candidate c = toCandidate(u, s, shared);
            c.scanPos = currentScanPos;
            c.pass = currentPass;
            c.fromArchiveMember = u.type == InputUnit.UnitType.OBJECT && u.archiveMemberName != null;
            if (c.hidden || c.binding == Binding.LOCAL) {
                local.computeIfAbsent(u.id, k -> new LinkedHashMap<>())
                        .putIfAbsent(new SymbolKey(s.name, s.versionName), c);
                continue;
            }
            SymbolKey key = new SymbolKey(s.name, s.versionName);
            global.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            byNameGlobal.computeIfAbsent(s.name, k -> new ArrayList<>()).add(c);
            // a default version node also answers the plain, unversioned name
            if (s.dynamic && s.versionName != null && s.versionDefault && s.isExportedVersioned()) {
                global.computeIfAbsent(SymbolKey.unversioned(s.name), k -> new ArrayList<>()).add(c);
            }
        }
    }

    public Candidate toCandidate(InputUnit u, ElfSymbol s, boolean shared) {
        Candidate c = new Candidate();
        c.unit = u;
        c.symbol = s;
        c.key = new SymbolKey(s.name, s.versionName);
        c.binding = s.binding;
        c.common = s.isCommon();
        c.defined = s.isDefined();
        c.hidden = s.visibility == Visibility.HIDDEN || s.visibility == Visibility.INTERNAL
                || (s.dynamic && !s.isExportedVersioned());
        c.protectedVis = s.visibility == Visibility.PROTECTED;
        c.shared = shared;
        c.value = s.value;
        c.size = s.size;
        c.sectionName = s.sectionName;
        return c;
    }

    public void addReferences(InputUnit u) {
        ParsedElf elf = u.elf;
        addRefs(u, elf.symbols, false);
        addRefs(u, elf.dynamicSymbols, true);
    }

    private void addRefs(InputUnit u, List<ElfSymbol> syms, boolean dynamic) {
        for (ElfSymbol s : syms) {
            if (s.name == null || s.name.isEmpty()) continue;
            if (s.isDefined()) continue;
            refs.add(new RefRecord(u, s.name, s.versionName, s.binding == Binding.WEAK, dynamic));
        }
    }

    /** Whether this archive (member) defines a name currently still needed. */
    public boolean providesNeededSymbol(InputUnit member, boolean weakExtracts) {
        for (ElfSymbol s : member.elf.symbols) {
            if (s.name == null || s.name.isEmpty() || !s.isDefined()) continue;
            if (s.binding == Binding.LOCAL) continue;
            if (s.visibility == Visibility.HIDDEN || s.visibility == Visibility.INTERNAL) continue;
            if (isNeeded(s.name, s.versionName, weakExtracts)) return true;
        }
        return false;
    }

    public boolean isNeeded(String name, String providedVersion, boolean weakExtracts) {
        for (RefRecord r : refs) {
            if (!r.name.equals(name)) continue;
            if (r.weak && !weakExtracts) continue;
            if (!versionSatisfies(r.requestedVersion, providedVersion)) continue;
            if (!hasObjectLevelProvider(r.key(), r)) return true;
        }
        return false;
    }

    /** True when a defined symbol version can answer a reference version. */
    public boolean versionSatisfies(String requested, String provided) {
        if (requested == null) return true; // unversioned ref; default alias handled at key level
        return requested.equals(provided);
    }

    public boolean hasObjectLevelProvider(SymbolKey key, RefRecord r) {
        List<Candidate> list = global.get(key);
        if (list == null) return false;
        for (Candidate c : list) {
            if (!c.defined) continue;
            if (!c.shared) return true;
            if (c.binding == Binding.GLOBAL) return true; // shared defs do satisfy ordinary refs
        }
        return false;
    }
}
