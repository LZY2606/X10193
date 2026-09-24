package com.tribunal.core;

/** 同一符号键下的一位候选提供者。 */
public class Candidate {
    public String object;
    public String bind;
    public String type;
    public String visibility;
    public String version;      // 显示用版本（@@v / @v / null）
    public boolean defined;
    public boolean common;
    public long size;
    public long value;
    public boolean eliminated;
    public String reason;       // 淘汰原因或“选中”

    public static Candidate of(String object, com.tribunal.elf.ElfFile.Symbol s) {
        Candidate c = new Candidate();
        c.object = object;
        c.bind = com.tribunal.elf.Elf.bindName(s.bind);
        c.type = com.tribunal.elf.Elf.typeName(s.type);
        c.visibility = com.tribunal.elf.Elf.visName(s.visibility);
        c.version = s.versionName == null ? null : (s.versionHidden ? "@" : "@@") + s.versionName;
        c.defined = !s.isUndefined();
        c.common = s.isCommon();
        c.size = s.size;
        c.value = s.value;
        return c;
    }
}
