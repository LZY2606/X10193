package court.symbol.elf;

public class ElfRelocation {
    public final int sectionIndex;
    public final String sectionName;
    public final long offset;
    public final int symbolIndex;
    public final int type;
    public final long addend;
    public final boolean rela;
    public final boolean dynamicSymbol;
    public String symbolName = "";

    public ElfRelocation(int sectionIndex, String sectionName, long offset, int symbolIndex, int type,
                         long addend, boolean rela, boolean dynamicSymbol) {
        this.sectionIndex = sectionIndex;
        this.sectionName = sectionName;
        this.offset = offset;
        this.symbolIndex = symbolIndex;
        this.type = type;
        this.addend = addend;
        this.rela = rela;
        this.dynamicSymbol = dynamicSymbol;
    }

    public boolean isCopy(int machine) {
        return machine == 62 && type == 5 || machine == 3 && type == 5;
    }

    public String typeName() {
        if (type == 5) {
            return "R_COPY";
        }
        if (type == 6) {
            return "R_GLOB_DAT";
        }
        if (type == 7) {
            return "R_JUMP_SLOT";
        }
        if (type == 8) {
            return "R_RELATIVE";
        }
        return "R_" + type;
    }
}
