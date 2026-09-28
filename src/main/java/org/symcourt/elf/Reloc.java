package org.symcourt.elf;

/** One relocation entry (REL or RELA). */
public final class Reloc {
    public final long offset;
    public final long addend;
    public final int symbolIndex;
    public final int type;
    public final boolean rela;
    public final String sectionName;
    public final int sectionIndex;

    public Reloc(long offset, long addend, int symbolIndex, int type,
                 boolean rela, String sectionName, int sectionIndex) {
        this.offset = offset;
        this.addend = addend;
        this.symbolIndex = symbolIndex;
        this.type = type;
        this.rela = rela;
        this.sectionName = sectionName;
        this.sectionIndex = sectionIndex;
    }

    public boolean isRelative(int machine) {
        return switch (machine) {
            case Elf.EM_X86_64 -> type == Elf.R_X86_64_RELATIVE;
            case Elf.EM_386 -> type == Elf.R_386_RELATIVE;
            default -> false;
        };
    }

    public boolean isCopy(int machine) {
        return switch (machine) {
            case Elf.EM_X86_64 -> type == Elf.R_X86_64_COPY;
            case Elf.EM_386 -> type == Elf.R_386_COPY;
            default -> false;
        };
    }

    public boolean isSymbolicSlot(int machine) {
        return switch (machine) {
            case Elf.EM_X86_64 -> type == Elf.R_X86_64_GLOB_DAT
                    || type == Elf.R_X86_64_JUMP_SLOT
                    || type == Elf.R_X86_64_64
                    || type == Elf.R_X86_64_PC32
                    || type == Elf.R_X86_64_PLT32
                    || type == Elf.R_X86_64_32;
            case Elf.EM_386 -> type == Elf.R_386_GLOB_DAT
                    || type == Elf.R_386_JMP_SLOT
                    || type == Elf.R_386_32
                    || type == Elf.R_386_PC32;
            default -> true;
        };
    }

    public String typeName(int machine) {
        return switch (machine) {
            case Elf.EM_X86_64 -> switch (type) {
                case R_X86_64_NONE -> "R_X86_64_NONE";
                case R_X86_64_64 -> "R_X86_64_64";
                case R_X86_64_PC32 -> "R_X86_64_PC32";
                case R_X86_64_PLT32 -> "R_X86_64_PLT32";
                case R_X86_64_COPY -> "R_X86_64_COPY";
                case R_X86_64_GLOB_DAT -> "R_X86_64_GLOB_DAT";
                case R_X86_64_JUMP_SLOT -> "R_X86_64_JUMP_SLOT";
                case R_X86_64_RELATIVE -> "R_X86_64_RELATIVE";
                case R_X86_64_32 -> "R_X86_64_32";
                default -> "R_X86_64_" + type;
            };
            case Elf.EM_386 -> switch (type) {
                case R_386_32 -> "R_386_32";
                case R_386_PC32 -> "R_386_PC32";
                case R_386_COPY -> "R_386_COPY";
                case R_386_GLOB_DAT -> "R_386_GLOB_DAT";
                case R_386_JMP_SLOT -> "R_386_JMP_SLOT";
                case R_386_RELATIVE -> "R_386_RELATIVE";
                default -> "R_386_" + type;
            };
            default -> "R_" + type;
        };
    }
}
