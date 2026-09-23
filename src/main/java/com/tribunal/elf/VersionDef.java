package com.tribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** A version definition (VERDEF). Index 0 = local, 1 = global are reserved. */
public class VersionDef {
    public int index;          // version index (vd_ndx)
    public String name;        // primary version name
    public List<String> aliases = new ArrayList<>(); // additional names (vd_cnt > 1)
    public boolean hidden;

    public List<String> allNames() {
        List<String> all = new ArrayList<>();
        all.add(name);
        all.addAll(aliases);
        return all;
    }
}
