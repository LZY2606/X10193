package com.symbolcourt.resolve;

/** Structured reason a candidate did not win. */
public final class RejectReason {
    public final String code;
    public final String detail;

    public RejectReason(String code, String detail) {
        this.code = code;
        this.detail = detail;
    }

    public static RejectReason laterOrder(String winner) {
        return new RejectReason("LOAD_ORDER",
                "same binding/version but '" + winner + "' appears earlier in load order");
    }

    public static RejectReason weakToStrong(String winner) {
        return new RejectReason("WEAK_TO_STRONG",
                "weak definition loses to strong definition in '" + winner + "'");
    }

    public static RejectReason commonToStrong(String winner) {
        return new RejectReason("COMMON_TO_STRONG",
                "common tentative definition loses to strong definition in '" + winner + "'");
    }

    public static RejectReason versionMismatch(String required) {
        return new RejectReason("VERSION_MISMATCH",
                "provider version does not match required '" + required + "'");
    }

    public static RejectReason hidden() {
        return new RejectReason("HIDDEN_VISIBILITY",
                "hidden/internal symbol is not exported outside its object");
    }

    public static RejectReason protectedInterpose() {
        return new RejectReason("PROTECTED_NON_INTERPOSABLE",
                "protected definition cannot be interposed by an external provider");
    }

    public static RejectReason scope() {
        return new RejectReason("OUT_OF_SCOPE",
                "shared object is not in this reference's dynamic scope");
    }

    public static RejectReason comdatDiscarded(String group) {
        return new RejectReason("COMDAT_DISCARDED",
                "section belongs to losing COMDAT group '" + group + "'");
    }

    public static RejectReason local() {
        return new RejectReason("LOCAL_BINDING",
                "local symbol is only addressable inside its own object");
    }

    public static RejectReason notExtracted() {
        return new RejectReason("ARCHIVE_NOT_EXTRACTED",
                "archive member was not needed before the fixpoint scan ended");
    }
}
