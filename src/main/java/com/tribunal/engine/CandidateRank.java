package com.tribunal.engine;

import com.tribunal.elf.Elf;
import com.tribunal.elf.ElfModel.Symbol;

/**
 * Symbol provider precedence as GNU ld/static+dynamic adjudication applies it.
 * Lower rank numbers win.
 *
 * <ol>
 *   <li>strong definition in a relocatable object (incl. extracted members)
 *       and protected/strong object data for COPY handling</li>
 *   <li>common (tentative) definition in an object</li>
 *   <li>weak definition in an object</li>
 *   <li>strong definition exported by a shared object</li>
 *   <li>weak definition exported by a shared object</li>
 * </ol>
 * The {@code commonOverWeak} policy can swap levels 2 and 3 when the user
 * selects a platform where weak definitions beat tentative symbols.
 */
final class CandidateRank {
    static final int OBJECT_STRONG = 0;
    static final int OBJECT_COMMON = 1;
    static final int OBJECT_WEAK = 2;
    static final int SHARED_STRONG = 3;
    static final int SHARED_WEAK = 4;

    private CandidateRank() {
    }

    static int rank(ParsedUnit unit, Symbol symbol, ResolvePolicy policy) {
        boolean shared = unit.kind == InputKind.SHARED;
        boolean common = symbol.isCommon();
        boolean weak = symbol.binding() == Elf.STB_WEAK;
        if (shared) {
            return weak ? SHARED_WEAK : SHARED_STRONG;
        }
        if (weak && !common) {
            return policy.commonOverWeak() ? OBJECT_WEAK : OBJECT_COMMON;
        }
        if (common) {
            return policy.commonOverWeak() ? OBJECT_COMMON : OBJECT_WEAK;
        }
        return OBJECT_STRONG;
    }

    static String rankName(int rank) {
        return switch (rank) {
            case OBJECT_STRONG -> "对象强定义";
            case OBJECT_COMMON -> "对象 common";
            case OBJECT_WEAK -> "对象弱定义";
            case SHARED_STRONG -> "共享库强定义";
            case SHARED_WEAK -> "共享库弱定义";
            default -> "未知(" + rank + ")";
        };
    }
}
