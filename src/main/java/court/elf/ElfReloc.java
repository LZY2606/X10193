package court.elf;

/**
 * Rel / Rela 条目。addend 仅 Rela 有（Rel 时为 0，hasAddend=false）。
 * typeName 在解析时按 machine 还原为可读重定位类型名。
 */
public final class ElfReloc {
    public final long offset;
    public final long info;
    public final long addend;
    public final boolean hasAddend;
    public final int symbolIndex;
    public final int type;
    public final String typeName;
    public final String sectionName;
    public final int sectionIndex;

    ElfReloc(long offset, long info, long addend, boolean hasAddend, int symClass,
             String typeName, String sectionName, int sectionIndex) {
        this.offset = offset;
        this.info = info;
        this.addend = addend;
        this.hasAddend = hasAddend;
        this.type = (int) info & (symClass == 64 ? 0xffffffff : 0xff);
        this.symbolIndex = symClass == 64 ? (int) (info >>> 32) : (int) (info >>> 8);
        this.typeName = typeName;
        this.sectionName = sectionName;
        this.sectionIndex = sectionIndex;
    }
}
