package com.tribunal.elf;

import java.util.List;
import java.util.Map;

/** Immutable result of parsing one ELF object (plus the raw bytes for re-parsing). */
public final class ElfModel {
    private ElfModel() {
    }

    public enum Class {ELF32, ELF64}

    public enum Endian {LITTLE, BIG}

    /** Parsed section header (zero based index corresponds to section table order). */
    public record Section(int index, int nameOffset, String name, int type, long flags, long addr,
                          long offset, long size, int link, int info, long addralign,
                          long entsize) {
        public boolean isGroupMember() {
            return (flags & Elf.SHF_GROUP) != 0;
        }
    }

    public record Symbol(int index, String name, int binding, int type, int visibility,
                         int shndx, long value, long size, int versionIndex,
                         String versionName, boolean hiddenVersion, boolean defaultVersion) {
        public boolean isLocal() {
            return binding == Elf.STB_LOCAL;
        }

        public boolean isWeak() {
            return binding == Elf.STB_WEAK;
        }

        public boolean isUndefined() {
            return shndx == Elf.SHN_UNDEF;
        }

        public boolean isCommon() {
            return shndx == Elf.SHN_COMMON || type == Elf.STT_COMMON;
        }

        public boolean isDefined() {
            return !isUndefined() && shndx != Elf.SHN_ABS && !isCommon();
        }

        public boolean isHidden() {
            return visibility == Elf.STV_HIDDEN || visibility == Elf.STV_INTERNAL;
        }

        public boolean isProtected() {
            return visibility == Elf.STV_PROTECTED;
        }
    }

    public record Relocation(int sectionIndex, long offset, long addend, long info,
                             int symbolIndex, int type, boolean rela, String symbolName) {
    }

    public record VersionDef(int index, String name, int flags, int ndx, List<String> parents) {
        public boolean base() {
            return (flags & Elf.VER_FLG_BASE) != 0;
        }

        public boolean weak() {
            return (flags & Elf.VER_FLG_WEAK) != 0;
        }
    }

    public record VersionNeed(String fileName, int versionIndex, String versionName,
                              boolean weak, List<String> parents) {
    }

    /**
     * A section group. signature is the signature symbol name (usually the mangled
     * COMDAT key); members are section indices.
     */
    public record SectionGroup(int sectionIndex, int flags, String signature,
                               List<Integer> members) {
        public boolean comdat() {
            return (flags & Elf.GRP_COMDAT) != 0;
        }
    }

    public record ElfFile(byte[] data, String sourceLabel, Class cls, Endian endian,
                          short type, short machine, long entry, long flags,
                          List<Section> sections, List<Symbol> symbols,
                          List<Relocation> relocations, List<SectionGroup> groups,
                          List<VersionDef> versionDefs, List<VersionNeed> versionNeeds,
                          Map<String, String> dynamicStrings, List<String> needed,
                          String soname, List<String> warnings) {
        public Section section(int index) {
            if (index < 0 || index >= sections.size()) {
                return null;
            }
            return sections.get(index);
        }

        public Symbol symbol(int index) {
            if (index < 0 || index >= symbols.size()) {
                return null;
            }
            return symbols.get(index);
        }

        public boolean isRelocatable() {
            return type == Elf.ET_REL;
        }

        public boolean isSharedObject() {
            return type == Elf.ET_DYN;
        }

        public boolean isExecutable() {
            return type == Elf.ET_EXEC;
        }
    }
}
