package court.model;

import java.util.Objects;

/**
 * 一个未定义引用的身份：来源单元 + 符号名 + 要求的版本名。
 * “相同名称不代表相同候选”：版本索引在进入引擎前已还原为版本名，
 * 所以 (foo@VERS_1.1) 与 (foo@VERS_2.0) 是两个不同的 RefKey。
 */
public final class RefKey {
    public final String unitId;
    public final String symbol;
    public final String versionName; // null 表示无版本引用；"" base 版本单独表示

    public RefKey(String unitId, String symbol, String versionName) {
        this.unitId = unitId;
        this.symbol = symbol;
        this.versionName = versionName;
    }

    public String versionLabel() {
        if (versionName == null) {
            return "无版本";
        }
        if (versionName.isEmpty()) {
            return "(BASE)";
        }
        return versionName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefKey other)) {
            return false;
        }
        return Objects.equals(unitId, other.unitId)
                && Objects.equals(symbol, other.symbol)
                && Objects.equals(versionName, other.versionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitId, symbol, versionName);
    }

    @Override
    public String toString() {
        return unitId + "::" + symbol + (versionName == null ? "" : "@" + versionName);
    }
}
