package com.gsb.tribunal.elf;

import java.util.List;

public record VerDef(int index, String name, List<String> aliases, boolean nonDefault) {
    public List<String> allNames() {
        var all = new java.util.ArrayList<String>();
        all.add(name);
        all.addAll(aliases);
        return all;
    }
}
