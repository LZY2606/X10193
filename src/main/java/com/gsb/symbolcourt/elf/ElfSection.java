package com.gsb.symbolcourt.elf;

public record ElfSection(
        int index,
        String name,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        int link,
        int info,
        long alignment,
        long entrySize,
        String groupSignature,
        boolean comdat,
        int groupId,
        byte[] raw
) {
    public boolean isAlloc() {
        return (flags & ElfConstants.SHF_ALLOC) != 0;
    }

    public boolean isGroupMember() {
        return (flags & ElfConstants.SHF_GROUP) != 0;
    }
}
