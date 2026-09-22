package gsb.tribunal.elf;

import java.util.List;

public record ComdatGroup(
        int groupSectionIndex,
        String signature,
        int flag,
        List<Integer> memberSections) {

    public static final int GRP_COMDAT = 1;
    public boolean comdat() { return (flag & GRP_COMDAT) != 0; }
}
