package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

/** 版本定义节点，如 VER_1 带别名 VER_0.9。 */
public class VersionDef {
    public int index;
    public int flags;
    /** 主名称（第一个 verdaux）。 */
    public String name;
    /** 别名（其余 verdaux）。 */
    public List<String> aliases = new ArrayList<>();

    public boolean base() {
        return (flags & com.symbolcourt.elf.Elf.VER_FLG_BASE) != 0;
    }

    public List<String> allNames() {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.addAll(aliases);
        return names;
    }
}
