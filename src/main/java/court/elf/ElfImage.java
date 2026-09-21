package court.elf;

import java.util.ArrayList;
import java.util.List;

/** Fully parsed image of one ELF file. Parse failures are captured, never thrown past the loader. */
public class ElfImage {
    public boolean corrupt;
    public String error;

    public byte elfClass;      // 1 = 32, 2 = 64
    public byte dataEncoding;  // 1 = LSB, 2 = MSB
    public int machine;
    public int type;
    public long entry;
    public boolean is64;
    public boolean le;

    public List<ElfSection> sections = new ArrayList<>();
    public List<ElfSymbol> symbols = new ArrayList<>();        // .symtab when present
    public List<ElfSymbol> dynSymbols = new ArrayList<>();     // .dynsym
    public List<ElfRelocation> relocations = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public String soname;

    public ElfSection sectionByIndex(int idx) {
        if (idx < 0 || idx >= sections.size()) return null;
        return sections.get(idx);
    }

    public ElfSection sectionByName(String n) {
        for (ElfSection s : sections) if (s.name.equals(n)) return s;
        return null;
    }

    public String typeName() {
        switch (type) {
            case Const.ET_REL: return "REL";
            case Const.ET_EXEC: return "EXEC";
            case Const.ET_DYN: return "DYN";
            default: return "ET" + type;
        }
    }
}
