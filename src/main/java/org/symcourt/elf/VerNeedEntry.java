package org.symcourt.elf;

/** One vernaux: a required version index for a needed shared object. */
public final class VerNeedEntry {
    public final int index;
    public final int flags;
    public final String name;
    public final String fromSoname;

    public VerNeedEntry(int index, int flags, String name, String fromSoname) {
        this.index = index;
        this.flags = flags;
        this.name = name;
        this.fromSoname = fromSoname;
    }
}
