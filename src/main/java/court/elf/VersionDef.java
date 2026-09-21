package court.elf;

import java.util.List;

/** A Verdef entry: one defined version, first aux name is the version name, later names are aliases. */
public class VersionDef {
    public int index;        // 1-based version index used by .gnu.version
    public int flags;
    public String name;      // primary name
    public List<String> aliases = java.util.List.of();
    public boolean base() { return (flags & Const.VER_FLG_BASE) != 0; }

    /** All names this version answers to. */
    public List<String> allNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(name);
        out.addAll(aliases);
        return out;
    }
}
