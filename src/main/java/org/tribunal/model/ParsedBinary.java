package org.tribunal.model;

import java.util.List;

public record ParsedBinary(long id, String name, String kind, String path, String sha256, long size,
                           boolean parseOk, String parseError, ElfFile elfFile,
                           List<ArchiveMember> members) {
    public ParsedBinary withId(long newId, String storedPath) {
        return new ParsedBinary(newId, name, kind, storedPath, sha256, size, parseOk, parseError, elfFile, members);
    }
}
