package com.symbolcourt.elf;

import java.util.List;

public record ElfFile(
        int elfClass,
        int objectType,
        int machine,
        List<Section> sections,
        List<Symbol> symtab,
        List<Symbol> dynsym,
        List<Relocation> relocations,
        List<Group> groups,
        List<Verdef> verdefs,
        List<Verneed> verneeds,
        int[] versym) {

    public static final int ET_REL = 1, ET_EXEC = 2, ET_DYN = 3;

    public record Section(int index, String name, int type, long flags, int link, int info,
                          long offset, long size, long entsize) {}

    public record Symbol(int index, String name, int bind, int type, int visibility,
                         int shndx, long value, long size) {
        public boolean isDefined() { return shndx != 0; }
        public boolean isCommon() { return shndx == 0xfff2; }
        public boolean isLocal() { return bind == 0; }
        public boolean isWeak() { return bind == 2; }
        public boolean isGlobal() { return bind == 1; }
    }

    public record Relocation(int sectionIndex, String sectionName, long offset,
                             int symIndex, long type, Long addend) {}

    public record Group(int sectionIndex, String signature, boolean comdat,
                        List<Integer> memberSections) {}

    public record Verdef(int index, String name, int flags, List<String> aliases) {}

    public record Verneed(String file, List<Entry> entries) {
        public record Entry(String name, int other) {}
    }

    /** 把 dynsym 索引处的版本索引还原为版本名（定义或需求）。 */
    public String versionName(int dynSymIndex) {
        if (versym == null || dynSymIndex < 0 || dynSymIndex >= versym.length) return null;
        int v = versym[dynSymIndex] & 0x7fff;
        if (v < 2) return null;
        for (Verdef d : verdefs) if (d.index() == v) return d.name();
        for (Verneed n : verneeds)
            for (Verneed.Entry e : n.entries()) if (e.other() == v) return e.name();
        return "VER#" + v;
    }

    /** true 表示非默认版本（name@ver），false 表示默认版本（name@@ver）或无版本。 */
    public boolean versionHidden(int dynSymIndex) {
        if (versym == null || dynSymIndex < 0 || dynSymIndex >= versym.length) return false;
        return (versym[dynSymIndex] & 0x8000) != 0;
    }

    public List<String> versionAliases(int dynSymIndex) {
        String name = versionName(dynSymIndex);
        if (name == null) return List.of();
        for (Verdef d : verdefs) if (d.name().equals(name)) return d.aliases();
        return List.of();
    }

    public String typeName() {
        return switch (objectType) {
            case ET_REL -> "REL";
            case ET_EXEC -> "EXEC";
            case ET_DYN -> "DYN";
            default -> "T" + objectType;
        };
    }
}
