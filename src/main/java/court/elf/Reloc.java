package court.elf;

/** One relocation entry (REL or RELA). */
public final class Reloc {
    public long offset;
    public long info;
    public Long addend;
    public int type;
    public int symIndex;
    public boolean rela;
    public String typeName;
    public ElfObject.Section section;
    public Sym symbol;
    public boolean copyReloc;
}
