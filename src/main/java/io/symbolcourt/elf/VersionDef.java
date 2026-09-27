package io.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** A VER_DEF chain entry: own version name plus its ancestor aliases. */
public class VersionDef {
    public int index;          // 1 based versym index
    public String name;
    public List<String> parentNames = new ArrayList<>();
    public boolean base;       // first definition, version "" baseline

    /**
     * A non default version definition is the anonymous base ("") or any
     * definition explicitly marked non default; GNU only emits the base as
     * non default.
     */
    public boolean nonDefault;
}
