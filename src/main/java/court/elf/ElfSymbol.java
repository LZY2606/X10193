package court.elf;

/**
 * 统一的符号视图（symtab/dynsym 合并后）。dynamic=true 表示来自 .dynsym；
 * versionIndex 为 versym 原始值（含 0x8000 非默认位），version 在解析后还原。
 */
public final class ElfSymbol {
    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;
    public static final int STB_GNU_UNIQUE = 10;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;
    public static final int STT_COMMON = 5;
    public static final int STT_GNU_IFUNC = 10;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_ABS = 0xfff1;
    public static final int SHN_COMMON = 0xfff2;

    public final int index; // 所在符号表内的索引（dynsym 从 0 开始）
    public final boolean dynamic;
    public final String name;
    public final int bind;
    public final int type;
    public final int visibility;
    public final int shndx;
    public final long value;
    public final long size;
    public int rawVersionIndex; // versym 原值；无版本信息时为 0
    public VersionNode version; // 还原后的版本节点；无版本信息为 null

    ElfSymbol(int index, boolean dynamic, String name, int bind, int type, int visibility,
              int shndx, long value, long size, int rawVersionIndex, VersionNode version) {
        this.index = index;
        this.dynamic = dynamic;
        this.name = name;
        this.bind = bind;
        this.type = type;
        this.visibility = visibility;
        this.shndx = shndx;
        this.value = value;
        this.size = size;
        this.rawVersionIndex = rawVersionIndex;
        this.version = version;
    }

    void setVersion(int rawIndex, VersionNode node) {
        this.rawVersionIndex = rawIndex;
        this.version = node;
    }

    public boolean isUndef() {
        return shndx == SHN_UNDEF;
    }

    public boolean isCommon() {
        return shndx == SHN_COMMON || (!isUndef() && type == STT_COMMON);
    }

    public boolean isDefined() {
        return !isUndef();
    }

    public boolean isLocal() {
        return bind == STB_LOCAL;
    }

    public boolean isWeak() {
        return bind == STB_WEAK;
    }

    public boolean isStrongGlobal() {
        return (bind == STB_GLOBAL || bind == STB_GNU_UNIQUE) && isDefined() && !isCommon();
    }

    public boolean isHiddenOrInternal() {
        return visibility == STV_HIDDEN || visibility == STV_INTERNAL;
    }

    public boolean isProtected() {
        return visibility == STV_PROTECTED;
    }

    /** 静态链接语义下的绑定类别名。 */
    public String bindLabel() {
        return switch (bind) {
            case STB_LOCAL -> "LOCAL";
            case STB_WEAK -> "WEAK";
            case STB_GNU_UNIQUE -> "GNU_UNIQUE";
            default -> "GLOBAL";
        };
    }

    public String visibilityLabel() {
        return switch (visibility) {
            case STV_INTERNAL -> "INTERNAL";
            case STV_HIDDEN -> "HIDDEN";
            case STV_PROTECTED -> "PROTECTED";
            default -> "DEFAULT";
        };
    }

    public String typeLabel() {
        return switch (type) {
            case STT_OBJECT -> "OBJECT";
            case STT_FUNC -> "FUNC";
            case STT_COMMON -> "COMMON";
            case STT_GNU_IFUNC -> "GNU_IFUNC";
            default -> "NOTYPE";
        };
    }
}
