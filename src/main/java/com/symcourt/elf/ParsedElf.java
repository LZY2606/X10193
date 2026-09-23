package com.symcourt.elf;

import java.util.List;

public record ParsedElf(
        String name,
        int elfType,
        List<Section> sections,
        List<Symbol> symbols,
        List<Relocation> relocations,
        List<VersionDef> versionDefs,
        List<VersionNeed> versionNeeds,
        List<Group> groups,
        List<String> needed) {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public record Section(int index, String name, int type, long flags, long offset, long size,
                          int link, int info, long entsize) {}

    public record Symbol(int index, String table, String name, int bind, int type, int visibility,
                         int shndx, long value, long size,
                         Integer versionIndex, boolean versionHidden, String versionName) {
        public boolean isUndefined() {
            return shndx == 0;
        }

        public boolean isCommon() {
            return shndx == 0xfff2;
        }

        public boolean isDefined() {
            return shndx != 0;
        }
    }

    public record Relocation(String section, long offset, long type, int symIndex, String symTable,
                             String symbolName, Long addend) {}

    public record VersionDef(int index, String name, boolean base) {}

    public record VersionNeed(String file, int index, String name) {}

    public record Group(String signature, List<Integer> memberSections, boolean comdat) {}

    public String typeName() {
        return switch (elfType) {
            case ET_REL -> "可重定位对象";
            case ET_EXEC -> "可执行文件";
            case ET_DYN -> "共享库";
            default -> "未知(" + elfType + ")";
        };
    }
}
