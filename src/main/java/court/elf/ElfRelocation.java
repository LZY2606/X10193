package court.elf;

/** A relocation record referencing a symbol in the section's linked symbol table. */
public class ElfRelocation {
    public int index;
    public long offset;
    public long type;
    public int symbolIndex;
    public long addend;
    public boolean rela;
    public String sectionName;
    public boolean writeTarget;   // offset points into a SHF_WRITE section (copy-like data reloc)
    public String computedType;   // architecture decoded mnemonic, e.g. R_X86_64_COPY
}
