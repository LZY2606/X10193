package com.tribunal.elf;

/** A version requirement (VERNEED vernaux entry). */
public class VersionNeed {
    public String file;   // vn_file: needed shared object
    public int index;     // vna_other: version index used by versym
    public String name;   // vna_name: required version name
    public boolean hidden;
}
