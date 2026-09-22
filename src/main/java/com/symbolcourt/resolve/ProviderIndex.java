package com.symbolcourt.resolve;

import com.symbolcourt.elf.ElfConstants;
import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.ElfSection;
import com.symbolcourt.elf.ElfSymbol;
import com.symbolcourt.model.Candidate;
import com.symbolcourt.model.LoadUnit;
import com.symbolcourt.model.SymbolKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Index of every externally-visible definition across the loaded units.
 * The same name maps to separate entries for each version, and the unversioned
 * key holds default versions separately from explicitly-versioned nodes.
 */
public final class ProviderIndex {

    public static final class Provider {
        public final LoadUnit unit;
        public final ElfSymbol symbol;
        public final Candidate candidate;

        Provider(LoadUnit unit, ElfSymbol symbol, Candidate candidate) {
            this.unit = unit;
            this.symbol = symbol;
            this.candidate = candidate;
        }
    }

    private final Map<SymbolKey, List<Provider>> providers = new HashMap<>();
    private final Map<String, List<Provider>> byName = new HashMap<>();

    public void add(SymbolKey key, Provider p) {
        providers.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        byName.computeIfAbsent(key.getName(), k -> new ArrayList<>()).add(p);
    }

    public List<Provider> exact(SymbolKey key) {
        return providers.getOrDefault(key, List.of());
    }

    public List<Provider> byName(String name) {
        return byName.getOrDefault(name, List.of());
    }

    public Map<SymbolKey, List<Provider>> all() {
        return providers;
    }

    /** Build a candidate from a definition symbol, applying COMDAT and rank rules. */
    public static Candidate toCandidate(LoadUnit u, ElfSymbol s, boolean comdatDiscardedSection,
                                        boolean comdatProvided) {
        Candidate.Rank rank;
        if (s.isCommon()) {
            rank = Candidate.Rank.COMMON;
        } else if (s.isWeak()) {
            rank = Candidate.Rank.WEAK_DEFINITION;
        } else {
            rank = Candidate.Rank.STRONG_DEFINITION;
        }
        long align = s.isCommon() ? maxAlign(s.getValue()) : 0;
        long address = s.getValue();
        return new Candidate(u.getUnitId(), u.getDisplayName(), u.getLoadIndex(),
                s.getBind(), s.getType(), s.getVisibility(), s.getVersionName(),
                s.isDefaultVersion(), s.isCommon(), s.getSize(), align,
                comdatProvided && !comdatDiscardedSection, address, rank);
    }

    private static long maxAlign(long v) {
        // For STT_COMMON, st_value encodes alignment; legacy SHN_COMMON encodes max alignment.
        return v;
    }

    public static boolean isExternallyProvided(ElfSymbol s, ElfFile elf,
                                                Set<String> discardedSections) {
        if (s.isLocal()) {
            return false;
        }
        if (!s.isDefined()) {
            return false;
        }
        if (s.isHiddenVisibility()) {
            return false;
        }
        if (s.getName() == null || s.getName().isEmpty()) {
            return false;
        }
        // Hidden version node (the non-default "foo@VER" used only inside the DSO).
        if (s.isDynamic() && s.isHiddenVersionNode() && s.getVersionSource()
                == ElfSymbol.VersionSource.VERDEF) {
            return false;
        }
        if (elf.getType() == ElfConstants.ET_REL) {
            ElfSection sec = elf.sectionByIndex(s.getShndx());
            if (sec != null && discardedSections != null
                    && discardedSections.contains(sec.getName())) {
                return false;
            }
        }
        return true;
    }
}
