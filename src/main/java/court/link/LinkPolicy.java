package court.link;

/**
 * Platform and resolution policy for a scheme. Changing policy (together with the
 * explicit load order) produces a new, immutable scheme: old results never drift.
 */
public class LinkPolicy {
    public int machine = 62;            // EM_X86_64 default
    public String archivePolicy = "REPEAT"; // REPEAT (GNU-like circular scan) | SINGLE_PASS
    public String tiePolicy = "ERROR";      // ERROR (preserve ambiguity) | LOAD_ORDER (first explicit)
    public String platformName = "x86-64";

    public static LinkPolicy x86_64() {
        LinkPolicy p = new LinkPolicy();
        p.machine = 62; p.platformName = "x86-64"; return p;
    }

    public static LinkPolicy aarch64() {
        LinkPolicy p = new LinkPolicy();
        p.machine = 183; p.platformName = "AArch64"; return p;
    }
}
