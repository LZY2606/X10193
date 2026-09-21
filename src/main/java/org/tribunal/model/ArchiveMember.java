package org.tribunal.model;

public record ArchiveMember(String name, boolean parseOk, String parseError, ElfFile elfFile) {
}
