package court.elf;

import java.util.List;

/** A Verneed: a needed DSO plus the version names it must provide. */
public class VersionNeed {
    public String file;
    public List<Entry> entries = new java.util.ArrayList<>();

    public static class Entry {
        public int index;     // version index referenced by .gnu.version
        public int flags;
        public String name;
        public boolean weak;
    }
}
