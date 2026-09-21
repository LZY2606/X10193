package court.elf;

/** Parsed section header plus resolved name. */
public class ElfSection {
    public int index;
    public String name;
    public int type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long addralign;
    public long entsize;

    // populated for SHT_GROUP: group flag word and member section indices
    public int groupFlag;
    public java.util.List<Integer> groupMembers = java.util.List.of();

    public boolean isComdat() {
        return type == Const.SHT_GROUP && (groupFlag & Const.GRP_COMDAT) != 0;
    }

    public String typeName() {
        switch (type) {
            case Const.SHT_PROGBITS: return "PROGBITS";
            case Const.SHT_SYMTAB: return "SYMTAB";
            case Const.SHT_STRTAB: return "STRTAB";
            case Const.SHT_RELA: return "RELA";
            case Const.SHT_REL: return "REL";
            case Const.SHT_DYNSYM: return "DYNSYM";
            case Const.SHT_GROUP: return "GROUP";
            case Const.SHT_NOBITS: return "NOBITS";
            case Const.SHT_DYNAMIC: return "DYNAMIC";
            case Const.SHT_HASH: return "HASH";
            default: return "SHT?" + type;
        }
    }
}
