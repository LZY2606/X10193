package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

public final class ElfTypes {
    private ElfTypes() {}

    public record SectionInfo(int index, String name, int type, long flags, long addr,
                              long offset, long size, int link, int info, long entsize) {
        public boolean nobits() { return type == 8; }
    }

    public record ElfSym(String name, int index, boolean dynamic, int bind, int type, int vis,
                         int shndx, long value, long size,
                         String version, boolean versionHidden, boolean versionDefault,
                         String versionFile, String sectionName) {
        public boolean undefined() { return shndx == 0; }
        public boolean common() { return shndx == 0xfff2; }
    }

    public record ElfReloc(boolean dynamic, boolean rela, int type, long offset, long addend,
                           int symIndex, String symbol, String targetSection) {}

    public record VerDefEntry(String name, int index, int flags, List<String> aliases) {
        public boolean defaultVersion() { return (flags & 0x1) != 0; }
    }

    public record VerNeedEntry(String file, String name, int index, int flags) {
        public boolean defaultVersion() { return (flags & 0x1) != 0; }
    }

    public record GroupInfo(int sectionIndex, String signature, long flags,
                            List<String> memberSections) {
        public boolean comdat() { return (flags & 0x1) != 0; }
    }

    public static class ElfFile {
        public final byte[] raw;
        public final int elfClass;
        public final boolean bigEndian;
        public final int type;
        public final int machine;
        public final List<SectionInfo> sections = new ArrayList<>();
        public final List<List<ElfSym>> symbolsBySection = new ArrayList<>();
        public final List<ElfSym> staticSymbols = new ArrayList<>();
        public final List<ElfSym> dynamicSymbols = new ArrayList<>();
        public final List<ElfReloc> relocations = new ArrayList<>();
        public final List<VerDefEntry> verDefs = new ArrayList<>();
        public final List<VerNeedEntry> verNeeds = new ArrayList<>();
        public final List<GroupInfo> groups = new ArrayList<>();
        public final List<String> needed = new ArrayList<>();
        public String soname;

        public ElfFile(byte[] raw, int elfClass, boolean bigEndian, int type, int machine) {
            this.raw = raw;
            this.elfClass = elfClass;
            this.bigEndian = bigEndian;
            this.type = type;
            this.machine = machine;
        }

        public boolean relocatable() { return type == 1; }
        public boolean shared() { return type == 3; }
        public boolean executable() { return type == 2; }
    }
}
