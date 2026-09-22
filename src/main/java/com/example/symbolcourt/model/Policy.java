package com.example.symbolcourt.model;

/** Target-platform resolution policy. Frozen into each case version; old results never drift. */
public class Policy {
    public String weakResolution = "STATIC";       // STATIC: strong overrides weak; DYNAMIC: load-order wins
    public String multipleStrong = "AMBIGUOUS";    // AMBIGUOUS: retain ties; FIRST: first explicit slot wins
    public boolean strongOverWeak = true;
    public boolean interposition = true;           // DSO internal references may be preempted
    public boolean copyRelocations = true;         // synthesize copy relocations for executable OBJECT refs

    public static Policy defaults() {
        return new Policy();
    }
}
