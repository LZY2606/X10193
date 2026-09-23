package com.tribunal.elf;

import java.util.ArrayList;
import java.util.List;

/** An SHT_GROUP section: COMDAT signature plus member section names. */
public class GroupInfo {
    public String signature;
    public boolean comdat;
    public List<String> sectionNames = new ArrayList<>();
}
