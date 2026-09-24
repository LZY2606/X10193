package court.engine;

/** Target-platform policy knobs. Changing a preset creates a new, separately stored scheme. */
public final class Policy {
    public enum Phase { MIXED, STATIC, DYNAMIC }
    public enum ProtectedMode { LOCAL, ERROR, PREEMPTIBLE }
    public enum TieMode { AMBIGUOUS, FIRST }

    public Phase phase = Phase.MIXED;
    public boolean weakExtractsArchives = false;
    public boolean hiddenNotExported = true;
    public ProtectedMode protectedMode = ProtectedMode.LOCAL;
    public TieMode tieMode = TieMode.AMBIGUOUS;
    public boolean copyRelocEnabled = true;
    /** Maximum lazy-extraction sweeps (archive cycle guard). */
    public int maxArchivePasses = 64;
    public String presetName = "GNU_DEFAULT";

    public static Policy gnuDefault() {
        return new Policy();
    }

    public static Policy gnuStatic() {
        Policy p = new Policy();
        p.phase = Phase.STATIC;
        p.presetName = "GNU_STATIC";
        return p;
    }

    public static Policy strictProtected() {
        Policy p = new Policy();
        p.protectedMode = ProtectedMode.ERROR;
        p.presetName = "STRICT_PROTECTED";
        return p;
    }
}
