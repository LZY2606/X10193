package court.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析完成的单个 ELF 对象（relocatable / shared object）。
 * unitId 由上层分配（上传对象或 archive 成员），解析层不关心来源。
 */
public final class ElfObject {
    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public String unitId;
    public String displayName;

    public boolean elf64;
    public boolean littleEndian = true;
    public int elfClass;
    public int machine;
    public int type;
    public String machineName = "UNKNOWN";

    public final List<ElfSection> sections = new ArrayList<>();
    public final List<ElfSymbol> symbols = new ArrayList<>();
    public final List<ElfReloc> relocs = new ArrayList<>();
    public final List<ComdatGroup> groups = new ArrayList<>();
    public final Map<Integer, VersionNode> versionsByIndex = new LinkedHashMap<>();

    public final List<String> needed = new ArrayList<>(); // DT_NEEDED
    public String soname; // DT_SONAME
    public String sourcePath;
    public String archiveMember; // 来自 archive 时的成员名

    public boolean isRelocatable() {
        return type == ET_REL;
    }

    public boolean isSharedObject() {
        return type == ET_DYN;
    }

    public ElfSection sectionAt(int idx) {
        if (idx < 0 || idx >= sections.size()) {
            return null;
        }
        return sections.get(idx);
    }

    public boolean isSectionDroppedByComdat(int shndx) {
        if (shndx <= 0) {
            return false;
        }
        for (ComdatGroup g : groups) {
            if (!g.kept && g.memberSections.contains(shndx)) {
                return true;
            }
        }
        return false;
    }
}
