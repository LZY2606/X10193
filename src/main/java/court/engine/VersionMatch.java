package court.engine;

import court.elf.ElfSymbol;
import court.elf.VersionNode;

/**
 * 版本匹配。引用方的要求来自还原后的版本名（RefKey），定义方携带 VersionNode。
 * 规则（不做“同名即同候选”的简化）：
 *  - 无版本引用只接受默认版本定义（非默认版本必须按确切名字点名）；
 *  - 有版本名要求按确切名字匹配，别名（同一 Verdef 节点的 aux 链）视为同一版本；
 *  - base 版本（VER_FLG_BASE，名字通常是 soname）按确切名匹配，不开放给无版本名引用；
 *  - 无版本信息的 relocatable 对象（version==null）只满足无版本引用。
 */
public final class VersionMatch {

    public enum Result {
        OK,
        NO_VERSION_ON_PROVIDER,
        NON_DEFAULT_NOT_NAMED,
        NAME_MISMATCH
    }

    public static Result check(String requiredVersion, VersionNode provider) {
        boolean reqNone = requiredVersion == null;
        if (provider == null) {
            return reqNone ? Result.OK : Result.NO_VERSION_ON_PROVIDER;
        }
        if (reqNone) {
            // 非默认版本不接受无版本引用；base 默认节点可以
            return provider.nonDefault ? Result.NON_DEFAULT_NOT_NAMED : Result.OK;
        }
        if (provider.name != null && provider.name.equals(requiredVersion)) {
            return Result.OK;
        }
        if (provider.aliases.contains(requiredVersion)) {
            return Result.OK;
        }
        return Result.NAME_MISMATCH;
    }

    public static String reason(Result r, String requiredVersion, ElfSymbol sym) {
        return switch (r) {
            case NO_VERSION_ON_PROVIDER ->
                    "引用要求版本 " + requiredVersion + "，但提供者 " + sym.name + " 没有版本信息";
            case NON_DEFAULT_NOT_NAMED ->
                    "提供者 " + sym.name + "@" + (sym.version != null ? sym.version.name : "?")
                            + " 是非默认版本，无版本引用不能绑定";
            case NAME_MISMATCH ->
                    "版本不符：要求 " + requiredVersion + "，提供者为 "
                            + (sym.version != null ? sym.version.name : "无版本");
            case OK -> null;
        };
    }

    private VersionMatch() {
    }
}
