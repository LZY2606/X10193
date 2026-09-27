package io.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** One VER_NEEDED entry (a needed shared object) with its required names. */
public class VersionNeed {
    public String file;
    public List<Aux> aux = new ArrayList<>();

    public static class Aux {
        public int index;       // 1 based versym index
        public String name;
        public boolean weak;    // VER_FLG_WEAK (still unsatisfied here if missing)
        public boolean hidden;
    }
}
