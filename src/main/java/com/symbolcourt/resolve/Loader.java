package com.symbolcourt.resolve;

import com.symbolcourt.elf.ArchiveMember;
import com.symbolcourt.elf.ArchiveParser;
import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.ElfParser;
import com.symbolcourt.elf.ElfSymbol;
import com.symbolcourt.model.InputEntry;
import com.symbolcourt.model.LoadUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns user input bytes into {@link LoadUnit}s and archive libraries.
 * Archive members are parsed lazily on extraction, but every member is
 * pre-parsed once to build the deterministic symbol index (ordered by the
 * archive's explicit member order, never by file-system traversal). Corrupt
 * objects and archive members are isolated to themselves.
 */
public final class Loader {

    private final ElfParser elfParser = new ElfParser();
    private final ArchiveParser archiveParser = new ArchiveParser();

    public static final class PendingMember {
        public final LoadUnit unit;
        public final byte[] data;
        boolean parsed;

        PendingMember(LoadUnit unit, byte[] data) {
            this.unit = unit;
            this.data = data;
        }

        /** Parse (once) the member and return the unit; corrupt members stay isolated. */
        public LoadUnit ensureParsed(ElfParser parser) {
            if (!parsed) {
                try {
                    ElfFile elf = parser.parse(data, unit.getDisplayName());
                    unit.markParsed(elf);
                } catch (RuntimeException ex) {
                    unit.markCorrupt(ex.getMessage());
                }
                parsed = true;
            }
            return unit;
        }
    }

    public static final class ArchiveLibrary {
        public final String archiveId;
        public final String displayName;
        public final int loadIndex;
        public final List<PendingMember> members = new ArrayList<>();
        public final Map<String, List<Integer>> symbolToMemberOrders = new LinkedHashMap<>();

        ArchiveLibrary(String archiveId, String displayName, int loadIndex) {
            this.archiveId = archiveId;
            this.displayName = displayName;
            this.loadIndex = loadIndex;
        }
    }

    public static final class LoadedInput {
        public final List<LoadUnit> directUnits = new ArrayList<>();
        public final List<ArchiveLibrary> archives = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
    }

    public LoadedInput load(List<InputEntry> entries, Map<String, byte[]> blobs,
                            java.util.function.Consumer<LoadUnit> corruptSink) {
        LoadedInput out = new LoadedInput();
        for (InputEntry e : entries) {
            byte[] data = blobs.get(e.unitId());
            if (data == null) {
                LoadUnit bad = LoadUnit.builder(e.unitId(), e.kind(), e.ordinal())
                        .displayName(e.displayName()).build();
                bad.markCorrupt("no binary stored for this input");
                out.directUnits.add(bad);
                if (corruptSink != null) {
                    corruptSink.accept(bad);
                }
                continue;
            }
            if (archiveParser.isArchive(data)) {
                ArchiveLibrary lib = new ArchiveLibrary(e.unitId(), e.displayName(), e.ordinal());
                out.archives.add(lib);
                try {
                    List<ArchiveMember> members = archiveParser.parse(data, e.displayName());
                    int order = 0;
                    for (ArchiveMember m : members) {
                        LoadUnit u = LoadUnit.builder(
                                        e.unitId() + "#" + m.getName(), InputEntry.Kind.ARCHIVE, -1)
                                .displayName(e.displayName() + "(" + m.getName() + ")")
                                .archive(e.unitId(), m.getName(), order)
                                .build();
                        PendingMember pm = new PendingMember(u, m.getData());
                        lib.members.add(pm);
                        // Pre-index by actually parsing (also isolates corrupt members early).
                        LoadUnit parsed = pm.ensureParsed(elfParser);
                        if (parsed.isParseOk()) {
                            for (String sym : providedGlobalNames(parsed.getElf())) {
                                lib.symbolToMemberOrders
                                        .computeIfAbsent(sym, k -> new ArrayList<>())
                                        .add(order);
                            }
                        }
                        order++;
                    }
                } catch (RuntimeException ex) {
                    out.warnings.add("archive " + e.displayName() + " unreadable: "
                            + ex.getMessage() + " (isolated)");
                }
            } else {
                LoadUnit unit = LoadUnit.builder(e.unitId(), e.kind(), e.ordinal())
                        .displayName(e.displayName()).build();
                parseInto(unit, data, e.displayName());
                out.directUnits.add(unit);
                if (!unit.isParseOk() && corruptSink != null) {
                    corruptSink.accept(unit);
                }
            }
        }
        return out;
    }

    private void parseInto(LoadUnit unit, byte[] data, String displayName) {
        try {
            ElfFile elf = elfParser.parse(data, data.length == 0 ? displayName : displayName);
            unit.markParsed(elf);
        } catch (RuntimeException ex) {
            unit.markCorrupt(ex.getMessage());
        }
    }

    /** Global, externally-visible provided names used to index archive members. */
    private List<String> providedGlobalNames(ElfFile elf) {
        List<String> names = new ArrayList<>();
        List<ElfSymbol> syms = elf.getStaticSymbols().isEmpty()
                ? elf.getDynamicSymbols() : elf.getStaticSymbols();
        for (ElfSymbol s : syms) {
            if (s.isLocal() || !s.isDefined() || s.isHiddenVisibility()) {
                continue;
            }
            if (s.getName() == null || s.getName().isEmpty()) {
                continue;
            }
            names.add(s.getName());
        }
        return names;
    }
}
