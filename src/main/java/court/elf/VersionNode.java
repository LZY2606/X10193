package court.elf;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdef / Verneed 的还原结果。版本索引（versym 里的 16 位值）在这里被还原为版本名。
 * vna_hash / vd_hash 等不参与裁决，仅保留名字、默认标志与别名链。
 */
public final class VersionNode {
    public final int index; // versym 使用的 1 基索引
    public String name;
    public boolean definition; // true=Verdef（本对象提供），false=Verneed（本对象需求）
    public boolean base; // verdef 带 VER_FLG_BASE（index 1 的 BASE 版本）
    public boolean weakNeed; // verneed aux 带 VER_FLG_WEAK：允许回落
    public final List<String> aliases = new ArrayList<>();
    public String neededFile; // verneed: 对应 DT_NEEDED 的名字
    public int neededFileIndex = -1; // vn_file 序号

    public VersionNode(int index) {
        this.index = index;
    }

    public boolean isDefault() {
        // 非默认版本由 versym 的 VERSYM_HIDDEN(0x8000) 标记；base 版本仍接受无版本引用。
        return !nonDefault;
    }

    public boolean nonDefault; // versym 中出现过 HIDDEN 位

    @Override
    public String toString() {
        return index + ":" + (definition ? "def" : "need") + ":" + name
                + (aliases.isEmpty() ? "" : aliases.toString());
    }
}
