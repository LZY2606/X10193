package gsb.tribunal.elf;

import java.util.List;

/** One DT_VERDEF node. First aux name is the node name; further aux entries are rename aliases. */
public record VersionDef(
        int index,
        int flags,
        List<String> names,
        Integer parentIndex) {

    public static final int VER_FLG_WEAK = 0x2;

    public String name() { return names.isEmpty() ? null : names.get(0); }
    public boolean hidden() { return (flags & VER_FLG_WEAK) != 0; }
}
