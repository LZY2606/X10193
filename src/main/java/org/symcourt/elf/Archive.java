package org.symcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed static archive: ordered members plus the library file name. */
public final class Archive {
    public final String fileName;
    public final List<ArchiveMember> members = new ArrayList<>();

    public Archive(String fileName) {
        this.fileName = fileName;
    }
}
