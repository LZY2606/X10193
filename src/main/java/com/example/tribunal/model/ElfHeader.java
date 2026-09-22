package com.example.tribunal.model;

public record ElfHeader(
        int elfClass,
        int dataEncoding,
        int machine,
        String machineName,
        int type,
        String typeName,
        boolean littleEndian,
        boolean valid
) {
}
