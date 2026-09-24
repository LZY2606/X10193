package court.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed view of one ELF object (ET_REL / ET_EXEC / ET_DYN). */
public final class ElfObject {
    public byte[] raw;
    public boolean elf64;
    public boolean littleEndian;
    public int elfClass;
    public int dataEncoding;
    public int machine;
    public String machineName;
    public int type;
    public String typeName;
    public long entry;

    public final List<Section> sections = new ArrayList<>();
    /** Indexed by ELF section index; null for SHT_NULL slots. */
    public final List<Section> sectionByIndex = new ArrayList<>();
    public final List<Sym> dynSyms = new ArrayList<>();
    public final List<Sym> staticSyms = new ArrayList<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public String soname;
    public final List<String> needed = new ArrayList<>();

    // Parsed version info (from .gnu.version*).
    /** Versym array aligned to dynSym indices; value is version index (e.g. 0x8002). */
    public int[] dynSymVersions = new int[0];
    /** version index (raw) -> version name; raw index is low 0x7fff. */
    public final Map<Integer, VerDefInfo> verDefs = new LinkedHashMap<>();
    public final List<VerNeed> verNeeds = new ArrayList<>();

    public Section section(String name) {
        for (Section s : sections) {
            if (name.equals(s.name)) return s;
        }
        return null;
    }

    public String versionName(int rawIndex) {
        if (rawIndex == 0 || rawIndex == 1) return null;
        int idx = rawIndex & 0x7fff;
        VerDefInfo vd = verDefs.get(idx);
        if (vd != null) return vd.name;
        for (VerNeed vn : verNeeds) {
            for (VerNeed.Aux aux : vn.aux) {
                if (aux.vers == idx) return aux.name;
            }
        }
        return null;
    }

    public String versionOf(Sym sym, boolean dynamic) {
        if (!dynamic) return null;
        int idx = sym.index < dynSymVersions.length ? dynSymVersions[sym.index] : 0;
        return versionName(idx);
    }

    public static final class Section {
        public int index;
        public String name = "";
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public int link;
        public int info;
        public long addralign;
        public long entsize;
        public Group group;
        public boolean discarded;
    }

    public static final class Group {
        public int flags;
        public String signature;
        public final List<Integer> members = new ArrayList<>();
    }

    public static final class VerDefInfo {
        public int ndx;
        public String name;
        public boolean base;
        public final List<String> parents = new ArrayList<>();
        public final List<String> aliases = new ArrayList<>();
    }

    public static final class VerNeed {
        public String file;
        public final List<Aux> aux = new ArrayList<>();
        public static final class Aux {
            public int vers;
            public String name;
        }
    }
}
