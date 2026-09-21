package court.engine;

import court.elf.ElfSymbol;
import court.model.LoadUnit;

/**
 * 静态 ld 语义下的候选层级（gABI 符号裁决状态表）：
 *  1 relocatable 中的强定义（GLOBAL/GNU_UNIQUE，非 common）
 *  2 relocatable 中的 common 暂定定义
 *  3 relocatable 中的弱定义
 *  4 共享对象中的默认定义
 *  5 共享对象中的弱定义
 */
public final class Rank {
    public static final int STRONG_REL = 1;
    public static final int COMMON_REL = 2;
    public static final int WEAK_REL = 3;
    public static final int SHARED_DEF = 4;
    public static final int SHARED_WEAK = 5;

    public static int of(LoadUnit unit, ElfSymbol sym, boolean common) {
        boolean shared = unit.elf.isSharedObject();
        if (shared) {
            return sym.isWeak() ? SHARED_WEAK : SHARED_DEF;
        }
        if (common) {
            return COMMON_REL;
        }
        return sym.isWeak() ? WEAK_REL : STRONG_REL;
    }

    public static String label(int rank) {
        return switch (rank) {
            case STRONG_REL -> "强定义 (relocatable)";
            case COMMON_REL -> "common 暂定";
            case WEAK_REL -> "弱定义 (relocatable)";
            case SHARED_DEF -> "共享库定义";
            case SHARED_WEAK -> "共享库弱定义";
            default -> "?";
        };
    }

    private Rank() {
    }
}
