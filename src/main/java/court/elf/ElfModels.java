package court.elf;

import java.util.List;

/** Immutable representations of the parsed ELF facts. Parsing never invokes a linker. */
public final class ElfModels {
    private ElfModels() {
    }

    public enum Class { ELF32, ELF64 }

    public enum Endian { LITTLE, BIG }

    public static final class VersionDef {
        public final int index;
        public final String name;
        public final List<String> parents;
        public final boolean base;
        public final boolean weak;

        public VersionDef(int index, String name, List<String> parents, boolean base, boolean weak) {
            this.index = index;
            this.name = name;
            this.parents = List.copyOf(parents);
            this.base = base;
            this.weak = weak;
        }
    }

    public static final class VersionNeed {
        public final String file;
        public final int index;
        public final String name;
        public final boolean weak;

        public VersionNeed(String file, int index, String name, boolean weak) {
            this.file = file;
            this.index = index;
            this.name = name;
            this.weak = weak;
        }
    }

    public static final class Group {
        public final String signature;
        public final int flags;
        public final List<Integer> sectionIndices;

        public Group(String signature, int flags, List<Integer> sectionIndices) {
            this.signature = signature;
            this.flags = flags;
            this.sectionIndices = List.copyOf(sectionIndices);
        }

        public boolean isComdat() {
            return (flags & 0x1) != 0;
        }
    }

    public static final class Relocation {
        public final long offset;
        public final long info;
        public final long addend;
        public final int symbolIndex;
        public final int type;
        public final int sectionIndex;
        public final boolean rela;

        public Relocation(long offset, long info, long addend, int sectionIndex, boolean rela,
                          Class elfClass) {
            this.offset = offset;
            this.info = info;
            this.addend = addend;
            this.sectionIndex = sectionIndex;
            this.rela = rela;
            if (elfClass == Class.ELF64) {
                this.symbolIndex = (int) (info >>> 32);
                this.type = (int) (info & 0xffffffffL);
            } else {
                this.symbolIndex = (int) (info >>> 8);
                this.type = (int) (info & 0xff);
            }
        }
    }

    public static final class Section {
        public final int index;
        public final String name;
        public final int type;
        public final long flags;
        public final long addr;
        public final long offset;
        public final long size;
        public final int link;
        public final int info;
        public final long addralign;
        public final long entsize;

        public Section(int index, String name, int type, long flags, long addr, long offset, long size,
                       int link, int info, long addralign, long entsize) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.flags = flags;
            this.addr = addr;
            this.offset = offset;
            this.size = size;
            this.link = link;
            this.info = info;
            this.addralign = addralign;
            this.entsize = entsize;
        }

        public boolean hasFlag(long flag) {
            return (flags & flag) != 0;
        }
    }

    public static final class Symbol {
        public final int index;
        public final String name;
        public final long value;
        public final long size;
        public final int bind;
        public final int type;
        public final int visibility;
        public final int sectionIndex;
        public final boolean dynamic;
        public final int rawVersionIndex;

        public Symbol(int index, String name, long value, long size, int bind, int type, int visibility,
                      int sectionIndex, boolean dynamic, int rawVersionIndex) {
            this.index = index;
            this.name = name;
            this.value = value;
            this.size = size;
            this.bind = bind;
            this.type = type;
            this.visibility = visibility;
            this.sectionIndex = sectionIndex;
            this.dynamic = dynamic;
            this.rawVersionIndex = rawVersionIndex;
        }

        public boolean isDefined() {
            return sectionIndex != 0;
        }

        public boolean isCommon() {
            return sectionIndex == 0xfff2;
        }

        public boolean isUndef() {
            return sectionIndex == 0 && (value == 0 || bind == 1);
        }

        public boolean isWeak() {
            return bind == 2;
        }
    }

    public static final class Header {
        public final Class elfClass;
        public final Endian endian;
        public final int type;
        public final int machine;
        public final long entry;
        public final long phoff;
        public final long shoff;
        public final int phentsize;
        public final int phnum;
        public final int shentsize;
        public final int shnum;
        public final int shstrndx;

        public Header(Class elfClass, Endian endian, int type, int machine, long entry, long phoff,
                      long shoff, int phentsize, int phnum, int shentsize, int shnum, int shstrndx) {
            this.elfClass = elfClass;
            this.endian = endian;
            this.type = type;
            this.machine = machine;
            this.entry = entry;
            this.phoff = phoff;
            this.shoff = shoff;
            this.phentsize = phentsize;
            this.phnum = phnum;
            this.shentsize = shentsize;
            this.shnum = shnum;
            this.shstrndx = shstrndx;
        }
    }

    public static final class ElfFile {
        public final Header header;
        public final List<Section> sections;
        public final List<Symbol> symbols;
        public final List<Symbol> dynsyms;
        public final List<Relocation> relocations;
        public final List<Relocation> dynRelocations;
        public final List<VersionDef> versionDefs;
        public final List<VersionNeed> versionNeeds;
        public final List<Group> groups;
        public final String soname;
        public final List<String> needed;

        public ElfFile(Header header, List<Section> sections, List<Symbol> symbols,
                       List<Symbol> dynsyms, List<Relocation> relocations,
                       List<Relocation> dynRelocations, List<VersionDef> versionDefs,
                       List<VersionNeed> versionNeeds, List<Group> groups, String soname,
                       List<String> needed) {
            this.header = header;
            this.sections = List.copyOf(sections);
            this.symbols = List.copyOf(symbols);
            this.dynsyms = List.copyOf(dynsyms);
            this.relocations = List.copyOf(relocations);
            this.dynRelocations = List.copyOf(dynRelocations);
            this.versionDefs = List.copyOf(versionDefs);
            this.versionNeeds = List.copyOf(versionNeeds);
            this.groups = List.copyOf(groups);
            this.soname = soname;
            this.needed = List.copyOf(needed);
        }

        public Section section(int index) {
            if (index < 0 || index >= sections.size()) {
                return null;
            }
            return sections.get(index);
        }
    }
}
