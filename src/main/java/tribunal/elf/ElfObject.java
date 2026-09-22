package tribunal.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fully parsed ELF unit: one relocatable object, one DSO, or one archive member. */
public class ElfObject {
    public String origin;             // file or "archive.a(member.o)"
    public boolean fromArchive;
    public String archiveName;
    public String memberName;
    public int elfClass;              // 1 = 32-bit, 2 = 64-bit
    public boolean littleEndian = true;
    public int machine;
    public int shStrndx;
    public int type;
    public long entry;
    public long flags;
    public List<Section> sections = new ArrayList<>();
    /** Entries of the primary (static) SHT_SYMTAB, when present. */
    public List<ElfSymbol> symbols = new ArrayList<>();
    /** Entries of SHT_DYNSYM; dynamic relocations and version tables index this list. */
    public List<ElfSymbol> dynSymbols = new ArrayList<>();
    public boolean hasDynSym;
    public List<Relocation> relocations = new ArrayList<>();
    public List<Group> groups = new ArrayList<>();
    public VersionInfo versionInfo = VersionInfo.EMPTY;
    public Map<Integer, String> versionByIndex = new LinkedHashMap<>();
    public List<String> needed = new ArrayList<>();
    public String soname;

    public boolean isShared() { return type == Elf.ET_DYN; }
    public boolean isRelocatable() { return type == Elf.ET_REL; }
    public boolean isExecutable() { return type == Elf.ET_EXEC; }

    public Section section(int index) {
        return (index >= 0 && index < sections.size()) ? sections.get(index) : null;
    }

    /** Resolve the textual version name attached to a dynamic symbol, or null. */
    public String versionName(Integer index) {
        if (index == null) return null;
        if (index == Elf.VER_NDX_LOCAL || index == Elf.VER_NDX_GLOBAL) return null;
        return versionByIndex.get(index);
    }
}
