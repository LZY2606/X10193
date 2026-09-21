package court.model;

import court.elf.ElfObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 参与裁决的一个“装载单元”：用户显式导入的对象、共享对象依赖、或被惰性抽取的 archive 成员。
 * ordinal 是在*显式加载顺序/依赖扩展顺序*中的位置；裁决中同票比较只允许使用 ordinal，
 * 绝不使用文件系统遍历顺序。
 */
public final class LoadUnit {
    public final String unitId;
    public final String name; // 文件/成员名
    public final ElfObject elf;
    public int ordinal;
    public final boolean fromArchive;
    public final String archiveId; // 抽取自哪个 archive（unitId）
    public boolean extracted; // archive 成员是否被抽取
    public boolean active = true; // COMDAT 去重 / archive 未抽取 / 损坏隔离 后是否参与
    public String inactiveReason;
    public final List<String> notes = new ArrayList<>();

    public LoadUnit(String unitId, String name, ElfObject elf, int ordinal,
                    boolean fromArchive, String archiveId) {
        this.unitId = unitId;
        this.name = name;
        this.elf = elf;
        this.ordinal = ordinal;
        this.fromArchive = fromArchive;
        this.archiveId = archiveId;
    }

    public String kindLabel() {
        if (elf == null) {
            return "BROKEN";
        }
        if (fromArchive) {
            return elf.isRelocatable() ? "ARCHIVE_MEMBER" : "ARCHIVE_MEMBER_DYN";
        }
        return elf.isRelocatable() ? "OBJECT" : "SHARED";
    }
}
