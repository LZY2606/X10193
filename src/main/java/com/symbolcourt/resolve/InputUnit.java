package com.symbolcourt.resolve;

import com.symbolcourt.model.ArchiveFile;
import com.symbolcourt.model.ArchiveMember;
import com.symbolcourt.model.ParsedElf;

public class InputUnit {
    public enum UnitType { OBJECT, ARCHIVE, SHARED, EXECUTABLE, CORRUPT }

    public final String id;
    public final UnitType type;
    public final int order;
    public final ParsedElf elf;
    public final ArchiveFile archive;
    public String archiveMemberName;
    public boolean extracted;
    public int extractStep = -1;
    public String extractReason;

    public InputUnit(String id, UnitType type, int order, ParsedElf elf, ArchiveFile archive) {
        this.id = id;
        this.type = type;
        this.order = order;
        this.elf = elf;
        this.archive = archive;
    }

    public String label() {
        return elf != null ? elf.displayName : (archive != null ? archive.displayName : id);
    }
}
