package com.tribunal.model;

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
        long entrySize
) {}
