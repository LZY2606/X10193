package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class ElfVersionNeed {
    public int index;
    public String file = "";
    public List<Entry> entries = new ArrayList<>();

    public static class Entry {
        public String name = "";
        public long flags;
        public int other;
        public boolean weak;
    }
}
