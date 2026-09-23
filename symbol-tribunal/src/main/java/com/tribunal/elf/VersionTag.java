package com.tribunal.elf;

/**
 * A resolved version index for this object: either a version definition
 * (.gnu.version_d) or a version requirement (.gnu.version_r). The ELF version
 * index (as stored in .gnu.version) is the identity; it is always reduced to a
 * human-readable name here.
 */
public final class VersionTag {
    public final int index;          // raw version index (>=2); needs stored as 0x8000|index
    public final String name;        // version name, e.g. "GLIBC_2.2.5"
    public final boolean definition; // true = verdef, false = verneed
    public final boolean nonDefault; // VER_FLG_WEAK set on def, or vna_flags weak on need
    public final String sourceFile;  // for requirements: the needed SONAME; else null

    public VersionTag(int index, String name, boolean definition, boolean nonDefault, String sourceFile) {
        this.index = index;
        this.name = name;
        this.definition = definition;
        this.nonDefault = nonDefault;
        this.sourceFile = sourceFile;
    }
}
