package com.symbolcourt.elf;

public class ArchiveMember {
    public final String name;
    public final byte[] content;
    public final long memberOffset;

    public ArchiveMember(String name, byte[] content, long memberOffset) {
        this.name = name;
        this.content = content;
        this.memberOffset = memberOffset;
    }
}
