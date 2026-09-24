package com.symbolcourt.model;

public class ArchiveMember {
    public String name;
    public long offset;
    public long size;
    public byte[] data;
    public ParsedElf elf;
    public boolean corrupt;
    public String error;
}
