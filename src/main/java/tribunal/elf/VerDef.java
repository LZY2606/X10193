package tribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class VerDef {
    public int index;
    public int flags;
    public boolean base;
    public final List<String> names = new ArrayList<>();
    public String name() { return names.isEmpty() ? "" : names.get(0); }
    public boolean isHidden() { return (flags & 0x2) != 0; }
}
