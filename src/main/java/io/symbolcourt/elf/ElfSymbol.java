package io.symbolcourt.elf;

/**
 * One Elf{32,64}_Sym entry with binding/type/visibility decoded.
 * {@code versionIndex} is the raw .gnu.version entry (VER_NDX_* or a 1 based
 * index into the version definition/requirement arrays).
 */
public class ElfSymbol {
    public int index;
    public String name = "";
    public long value;
    public long size;
    public int rawInfo;
    public int binding;       // STB_*
    public int type;          // STT_*
    public int visibility;    // STV_*
    public short sectionIndex; // SHN_* or real section index
    public boolean dynamic;
    public int versionIndex;  // 0/1 special or versym value; -1 when absent
    public String versionName; // resolved name, null for local/global base
    public boolean versionHidden; // bit 0x8000 of versym (VER_NDX_HIDDEN)

    public boolean isLocal() {
        return binding == 0;
    }

    public boolean isWeak() {
        return binding == 2;
    }

    public boolean isGlobal() {
        return binding == 1;
    }

    public boolean isCommon() {
        return sectionIndex == 0xfff5; // SHN_COMMON
    }

    public boolean isUndefined() {
        return sectionIndex == 0; // SHN_UNDEF
    }

    public boolean isDefined() {
        return !isUndefined() && !isCommon();
    }

    public boolean isProtected() {
        return visibility == 3;
    }

    public boolean isHidden() {
        return visibility == 2;
    }

    public String bindingName() {
        return switch (binding) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "BIND_" + binding;
        };
    }

    public String typeName() {
        return switch (type) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "TYPE_" + type;
        };
    }

    public String visibilityName() {
        return switch (visibility) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            default -> "VIS_" + visibility;
        };
    }
}
