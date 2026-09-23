package com.verdict.elf;

public final class ElfRelocation {
    /** Name of the section the relocation applies to. */
    public String targetSection;
    public long offset;
    public long type;
    public int symbolIndex;
    public String symbolName;
    public Long addend;
    public boolean rela;
}
