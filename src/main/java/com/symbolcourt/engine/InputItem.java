package com.symbolcourt.engine;

import com.symbolcourt.elf.ArchiveMember;
import com.symbolcourt.elf.ElfFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InputItem {
    public final int loadIndex;
    public final String id;
    public final String displayName;
    public final boolean archive;
    public final byte[] content;
    public ElfFile elf;
    public String corruptReason;
    public List<ArchiveMember> memberList = List.of();
    public final Map<String, ElfFile> memberElves = new LinkedHashMap<>();

    public InputItem(int loadIndex, String id, String displayName, boolean archive, byte[] content) {
        this.loadIndex = loadIndex;
        this.id = id;
        this.displayName = displayName;
        this.archive = archive;
        this.content = content;
    }
}
