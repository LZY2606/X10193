package court.elf;

import java.util.List;

/**
 * SHT_GROUP（COMDAT 节区组）。signature 为组签名字符名（来自 GRP_COMDAT 关联符号）。
 * memberSections 为成员 section 索引。kept=false 表示被去重丢弃。
 */
public final class ComdatGroup {
    public static final long GRP_COMDAT = 0x1L;

    public final String signature;
    public final boolean comdat;
    public final int ownerSymbolIndex;
    public final List<Integer> memberSections;
    public boolean kept = true;
    public String discardReason;

    public ComdatGroup(String signature, boolean comdat, int ownerSymbolIndex, List<Integer> members) {
        this.signature = signature;
        this.comdat = comdat;
        this.ownerSymbolIndex = ownerSymbolIndex;
        this.memberSections = members;
    }
}
