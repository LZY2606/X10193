package gsb.tribunal.elf;

public record ElfSection(
        int index,
        String name,
        int type,
        long flags,
        long addr,
        long offset,
        long size,
        int link,
        int info,
        long addralign,
        long entsize) {

    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_RELA = 4;
    public static final int SHT_HASH = 5;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_NOBITS = 8;
    public static final int SHT_REL = 9;
    public static final int SHT_GROUP = 17;
    public static final int SHT_DYNSYM = 11;

    public static final long SHF_ALLOC = 2L;
    public static final long SHF_EXECINSTR = 4L;
    public static final long SHF_WRITE = 1L;
    public static final long SHF_GROUP = 0x200L;

    public boolean isGrouped() { return (flags & SHF_GROUP) != 0; }
    public boolean alloc() { return (flags & SHF_ALLOC) != 0; }
}
