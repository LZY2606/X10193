package tribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** 解析后的 ELF 对象（或归档成员）的内存模型。所有偏移读取均带边界检查。 */
public class ElfData {
    public boolean elf64;
    public boolean littleEndian;
    public int type;
    public int machine;
    public String machineName = "UNKNOWN";
    public String soname;
    public boolean corrupt;
    public String error;
    public final List<Section> sections = new ArrayList<>();
    public final List<SymV> symtab = new ArrayList<>();
    public final List<SymV> dynsym = new ArrayList<>();
    public final List<Rela> relas = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public final List<VerDef> verdefs = new ArrayList<>();
    public final List<VerNeed> verneeds = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    /** versym 表：下标为 dynsym 下标，值为版本索引（含 VERSYM_HIDDEN 位）。 */
    public final List<Integer> versym = new ArrayList<>();

    public Section section(int idx) {
        if (idx < 0 || idx >= sections.size()) return null;
        return sections.get(idx);
    }

    public boolean isRelocatable() { return type == 1; }
    public boolean isShared() { return type == 3; }
    public boolean isExec() { return type == 2; }

    public List<SymV> allSymbols() {
        List<SymV> all = new ArrayList<>(symtab);
        all.addAll(dynsym);
        return all;
    }
}
