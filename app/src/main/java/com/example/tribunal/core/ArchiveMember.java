package com.example.tribunal.core;

public record ArchiveMember(
        String id,
        String name,
        long headerOffset,
        long dataOffset,
        long dataSize,
        ElfObject parsedObject,
        String parseError
) {
    public boolean extracted() {
        return parsedObject != null;
    }
}
