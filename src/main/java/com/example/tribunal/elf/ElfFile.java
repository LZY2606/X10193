package com.example.tribunal.elf;

import java.util.List;

/**
 * Parsed view of one ELF object file. Parsing is read-only: the raw bytes are
 * retained but never modified.
 */
public final class ElfFile {

    private final String displayName;
    private final String origin;
    private final byte[] bytes;

    private final boolean elf64;
    private final boolean littleEndian;
    private final int elfClass;
    private final int type;
    private final int machine;
    private final long entry;
    private final long flags;

    private final List<ElfSection> sections;
    private final List<ElfSymbol> staticSymbols;
    private final List<ElfSymbol> dynamicSymbols;
    private final List<ElfRelocation> relocations;
    private final VersionRecords versions;
    private final List<Group> groups;
    private final List<String> dependencies;
    private final String soname;

    public ElfFile(String displayName, String origin, byte[] bytes,
                   boolean elf64, boolean littleEndian, int elfClass,
                   int type, int machine, long entry, long flags,
                   List<ElfSection> sections,
                   List<ElfSymbol> staticSymbols,
                   List<ElfSymbol> dynamicSymbols,
                   List<ElfRelocation> relocations,
                   VersionRecords versions,
                   List<Group> groups,
                   List<String> dependencies,
                   String soname) {
        this.displayName = displayName;
        this.origin = origin;
        this.bytes = bytes;
        this.elf64 = elf64;
        this.littleEndian = littleEndian;
        this.elfClass = elfClass;
        this.type = type;
        this.machine = machine;
        this.entry = entry;
        this.flags = flags;
        this.sections = sections;
        this.staticSymbols = staticSymbols;
        this.dynamicSymbols = dynamicSymbols;
        this.relocations = relocations;
        this.versions = versions;
        this.groups = groups;
        this.dependencies = dependencies;
        this.soname = soname;
    }

    public String getDisplayName() { return displayName; }
    public String getOrigin() { return origin; }
    public byte[] getBytes() { return bytes; }
    public boolean isElf64() { return elf64; }
    public boolean isLittleEndian() { return littleEndian; }
    public int getElfClass() { return elfClass; }
    public int getType() { return type; }
    public int getMachine() { return machine; }
    public long getEntry() { return entry; }
    public long getFlags() { return flags; }
    public List<ElfSection> getSections() { return sections; }
    public List<ElfSymbol> getStaticSymbols() { return staticSymbols; }
    public List<ElfSymbol> getDynamicSymbols() { return dynamicSymbols; }
    public List<ElfRelocation> getRelocations() { return relocations; }
    public VersionRecords getVersions() { return versions; }
    public List<Group> getGroups() { return groups; }
    public List<String> getDependencies() { return dependencies; }
    public String getSoname() { return soname; }

    public boolean isRelocatable() { return type == 1; }   // ET_REL
    public boolean isSharedObject() { return type == 3; }  // ET_DYN
    public boolean isExecutable() { return type == 2; }    // ET_EXEC

    public static final class Group {
        private final String signature;
        private final int flags;
        private final List<Integer> memberSections;

        public Group(String signature, int flags, List<Integer> memberSections) {
            this.signature = signature;
            this.flags = flags;
            this.memberSections = memberSections;
        }

        public String getSignature() { return signature; }
        public int getFlags() { return flags; }
        public List<Integer> getMemberSections() { return memberSections; }
        public boolean isComdat() { return (flags & 1) == 1; }
    }
}
