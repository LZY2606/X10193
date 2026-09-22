package court.symbol.elf;

import java.util.List;
import java.util.Map;

/**
 * Immutable in-memory representation of a parsed ELF object (ET_REL or ET_DYN).
 * Nested records mirror the ELF structures that the arbitration engine needs.
 */
public final class Elf {

    private Elf() {
    }

    // ---- ELF header constants ------------------------------------------------
    public static final int EI_NIDENT = 16;
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_PROGBITS = 1;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_SYMTAB_SHNDX = 18;
    public static final int SHT_GNU_HASH = 0x6ffffff6;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int SHF_WRITE = 0x1;
    public static final int SHF_ALLOC = 0x2;
    public static final int SHF_EXECINSTR = 0x4;
    public static final int SHF_GROUP = 0x200;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_SECTION = 3;
    public static final int STT_FILE = 4;
    public static final int STT_COMMON = 5;
    public static final int STT_TLS = 6;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_XINDEX = 0xffff;

    // x86-64 relocations
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_GLOB_DAT = 6;
    public static final int R_X86_64_JUMP_SLOT = 7;
    public static final int R_X86_64_RELATIVE = 8;
    // AArch64 relocations (platform target switch exercises these too)
    public static final int R_AARCH64_COPY = 1024;
    public static final int R_AARCH64_GLOB_DAT = 1025;
    public static final int R_AARCH64_JUMP_SLOT = 1026;
    public static final int R_AARCH64_RELATIVE = 1027;

    public static final int DT_NULL = 0;
    public static final int DT_NEEDED = 1;
    public static final int DT_SONAME = 14;
    public static final int DT_VERDEFNUM = 0x6ffffffc;
    public static final int DT_VERNEEDNUM = 0x6fffffff;

    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;
    public static final int VER_FLG_BASE = 0x1;
    public static final int VER_FLG_WEAK = 0x2;
    public static final int VERSYM_HIDDEN = 0x8000;

    // ---- Parsed structures ---------------------------------------------------

    public enum VersionFlavor { NONE, DEFAULT, NON_DEFAULT, LOCAL, GLOBAL, UNRESOLVED }

    /** A symbol version: index restored to its textual name. */
    public record VersionRef(int index, String name, boolean hiddenBit, VersionFlavor flavor) {
        public String display() {
            return switch (flavor) {
                case NONE, GLOBAL -> null;
                case LOCAL -> "<local-version>";
                case UNRESOLVED -> name + " (未满足)";
                default -> name;
            };
        }
    }

    public record Symbol(
            int index,
            String name,
            int binding,
            int type,
            int visibility,
            long value,
            long size,
            int shndx,
            boolean defined,
            boolean common,
            VersionRef version) {

        public boolean weak() {
            return binding == STB_WEAK;
        }

        public boolean strong() {
            return binding == STB_GLOBAL;
        }

        public boolean local() {
            return binding == STB_LOCAL;
        }

        public boolean hiddenVis() {
            return visibility == STV_HIDDEN || visibility == STV_INTERNAL
                    || version != null && version.hiddenBit();
        }

        public boolean protectedVis() {
            return visibility == STV_PROTECTED;
        }

        public String bindingName() {
            return switch (binding) {
                case STB_LOCAL -> "LOCAL";
                case STB_GLOBAL -> "STRONG";
                case STB_WEAK -> "WEAK";
                default -> "BIND(" + binding + ")";
            };
        }

        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT";
                case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN";
                case STV_PROTECTED -> "PROTECTED";
                default -> "VIS(" + visibility + ")";
            };
        }
    }

    public record Relocation(long offset, long addend, int type, int symbolIndex,
                             int sectionIndex, boolean rela, boolean copyReloc,
                             String targetSection) {
    }

    public record VersionDefinition(int ndx, String name, List<String> aliases, int flags) {
        public boolean base() {
            return (flags & VER_FLG_BASE) != 0;
        }
    }

    public record VersionNeed(String soname, int ndx, String name, boolean weak) {
    }

    public record SectionGroup(int sectionIndex, long signatureSymIndex,
                               boolean comdat, List<Integer> memberSections) {
        public String signature(ElfFile file) {
            if (signatureSymIndex < 0 || signatureSymIndex >= file.symbols().size()) {
                return null;
            }
            return file.symbols().get((int) signatureSymIndex).name();
        }
    }

    public record Header(boolean elf64, boolean littleEndian, int type, int machine,
                         long entry, int shstrIndex) {
        public String machineName() {
            return switch (machine) {
                case 62 -> "x86-64";
                case 183 -> "aarch64";
                case 3 -> "x86";
                default -> "EM(" + machine + ")";
            };
        }
    }

    public record Section(int index, int nameIndex, String name, int type, long flags,
                          long addr, long offset, long size, int link, int info,
                          long alignment, long entrySize) {
        public boolean alloc() {
            return (flags & SHF_ALLOC) != 0;
        }

        public boolean writable() {
            return (flags & SHF_WRITE) != 0;
        }

        public boolean groupFlag() {
            return (flags & SHF_GROUP) != 0;
        }
    }

    /** A symbol table plus its companion data; either .symtab or .dynsym. */
    public record SymbolTable(Section section, List<Symbol> symbols,
                              Map<Integer, List<Relocation>> relocationsBySection,
                              boolean dynamic) {
    }
}
