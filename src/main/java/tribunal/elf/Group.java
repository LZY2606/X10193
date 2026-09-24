package tribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class Group {
    public boolean comdat;
    public int flags;
    public SymV signature;
    public String signatureName;
    public int sectionHeaderIndex;
    public final List<Integer> memberSections = new ArrayList<>();
}
