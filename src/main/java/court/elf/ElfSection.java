package court.elf;

/**
 * 单个 section header 的镜像。nameOffset 是 sh_name 原始值（.shstrtab 内偏移），
 * name 在解析时还原为字符串。
 */
public final class ElfSection {
    public static final long SHT_PROGBITS = 1;
    public static final long SHT_SYMTAB = 2;
    public static final long SHT_STRTAB = 3;
    public static final long SHT_RELA = 4;
    public static final long SHT_HASH = 5;
    public static final long SHT_DYNAMIC = 6;
    public static final long SHT_GROUP = 17;
    public static final long SHT_DYNSYM = 11;
    public static final long SHT_REL = 9;

    public final int index;
    public final long nameOffset;
    public String name = "";
    public final long type;
    public final long flags;
    public final long addr;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long addralign;
    public final long entsize;

    ElfSection(int index, long nameOffset, long type, long flags, long addr, long offset, long size,
               int link, int info, long addralign, long entsize) {
        this.index = index;
        this.nameOffset = nameOffset;
        this.type = type;
        this.flags = flags;
        this.addr = addr;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.addralign = addralign;
        this.entsize = entsize;
    }
}
