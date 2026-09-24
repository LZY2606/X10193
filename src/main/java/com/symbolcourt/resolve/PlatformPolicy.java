package com.symbolcourt.resolve;

public class PlatformPolicy {

    public enum CommonResolution { WARN, ERROR }
    public enum ProtectedData { ERROR, RESOLVE_LOCAL, COPY }
    public enum AsNeeded { IGNORE, HONOR }

    public final String id;
    public final String displayName;
    public final CommonResolution commonPolicy;
    public final ProtectedData protectedDataPolicy;
    public final AsNeeded asNeededPolicy;
    public final boolean multipleStrongIsError;
    public final boolean extractWeakSatisfies;

    public PlatformPolicy(String id, String displayName, CommonResolution commonPolicy,
                          ProtectedData protectedDataPolicy, AsNeeded asNeededPolicy,
                          boolean multipleStrongIsError, boolean extractWeakSatisfies) {
        this.id = id;
        this.displayName = displayName;
        this.commonPolicy = commonPolicy;
        this.protectedDataPolicy = protectedDataPolicy;
        this.asNeededPolicy = asNeededPolicy;
        this.multipleStrongIsError = multipleStrongIsError;
        this.extractWeakSatisfies = extractWeakSatisfies;
    }

    public static PlatformPolicy gnuLd() {
        return new PlatformPolicy("gnu-ld", "GNU ld (默认)", CommonResolution.WARN,
                ProtectedData.ERROR, AsNeeded.IGNORE, true, true);
    }

    public static PlatformPolicy gnuLdAsNeeded() {
        return new PlatformPolicy("gnu-ld-asneeded", "GNU ld --as-needed", CommonResolution.WARN,
                ProtectedData.ERROR, AsNeeded.HONOR, true, true);
    }

    public static PlatformPolicy strict() {
        return new PlatformPolicy("strict", "严格 (-fno-common)", CommonResolution.ERROR,
                ProtectedData.RESOLVE_LOCAL, AsNeeded.IGNORE, true, true);
    }
}
