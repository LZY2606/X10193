package com.symbolcourt.resolve;

import com.symbolcourt.elf.ElfFile;

import java.util.ArrayList;
import java.util.List;

/**
 * A unit participating in a link: an explicit object/shared object, or an
 * archive member extracted lazily.
 */
public final class LoadedUnit {
    public final String id;
    public final String label;
    public final UnitKind kind;
    public final ElfFile elf;
    /** Archive this was extracted from, null for explicit inputs. */
    public final String archiveLabel;
    /** Archive member name when extracted. */
    public final String memberName;
    public final int loadOrder;
    public final List<String> needed = new ArrayList<>();
    /** Sections discarded because their COMDAT group lost. */
    public final List<Integer> discardedSections = new ArrayList<>();

    public LoadedUnit(String id, String label, UnitKind kind, ElfFile elf,
                      String archiveLabel, String memberName, int loadOrder) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.elf = elf;
        this.archiveLabel = archiveLabel;
        this.memberName = memberName;
        this.loadOrder = loadOrder;
        if (elf != null) {
            this.needed.addAll(elf.needed);
        }
    }

    public boolean fromArchive() {
        return archiveLabel != null;
    }
}
