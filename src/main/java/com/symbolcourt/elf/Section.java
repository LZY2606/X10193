package com.symbolcourt.elf;

public record Section(
        int index,
        String name,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        int link,
        int info,
        long addralign,
        long entsize,
        long groupSignature,
        Integer groupIndex) {

    public boolean isAlloc() {
        return (flags & ElfConstants.SHF_ALLOC) != 0;
    }

    public boolean isWritable() {
        return (flags & ElfConstants.SHF_WRITE) != 0;
    }

    public boolean isExecutable() {
        return (flags & ElfConstants.SHF_EXECINSTR) != 0;
    }
}
