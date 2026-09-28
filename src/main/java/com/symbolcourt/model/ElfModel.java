package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed model of one ELF relocatable object, shared object, or archive member.
 * Immutable after parsing; the original binary bytes are never modified.
 */
public final class ElfModel {

    public enum Kind { RELOCATABLE, SHARED, EXECUTABLE, OTHER }

    public enum Binding { LOCAL, GLOBAL, WEAK, OTHER }
    public enum SymbolType { NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON_HINT, TLS, OTHER }
    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED, OTHER }

    public static final class VersionEntity {
        public final int index;
        public final boolean nonDefault;
        public final boolean weak;
        public final List<String> names = new ArrayList<>();
        public String needName;
        public boolean needWeak;

        public VersionEntity(int index, boolean nonDefault, boolean weak) {
            this.index = index;
            this.nonDefault = nonDefault;
            this.weak = weak;
        }

        public String primaryName() { return names.isEmpty() ? (needName != null ? needName : null) : names.get(0); }
        @Override public String toString() { return index + ":" + primaryName() + (nonDefault ? "(nd)" : ""); }
    }

    public static final class Section {
        public final int index;
        public final String name;
        public final int type;
        public final long flags;
        public final int link;
        public final int info;
        public final long groupSignature;
        public final boolean comdatGroup;
        public final int groupIndex;
        public final long address;
        public final long size;

        public Section(int index, String name, int type, long flags, int link, int info,
                       int groupIndex, long groupSignature, boolean comdatGroup, long address, long size) {
            this.index = index; this.name = name; this.type = type; this.flags = flags;
            this.link = link; this.info = info; this.groupIndex = groupIndex;
            this.groupSignature = groupSignature; this.comdatGroup = comdatGroup;
            this.address = address; this.size = size;
        }
    }

    public static final class Symbol {
        public final int index;
        public final String name;
        public final long value;
        public final long size;
        public final Binding binding;
        public final SymbolType type;
        public final Visibility visibility;
        public final short sectionIndex;
        public final int versionIndexRaw;
        public final VersionEntity version;

        public Symbol(int index, String name, long value, long size, Binding binding, SymbolType type,
                      Visibility visibility, short sectionIndex, int versionIndexRaw, VersionEntity version) {
            this.index = index; this.name = name; this.value = value; this.size = size;
            this.binding = binding; this.type = type; this.visibility = visibility;
            this.sectionIndex = sectionIndex; this.versionIndexRaw = versionIndexRaw; this.version = version;
        }

        public boolean isUndefined() { return sectionIndex == 0; } // SHN_UNDEF
        public boolean isCommon() { return sectionIndex == (short) 0xfff2 || type == SymbolType.COMMON_HINT; } // SHN_COMMON
        public String versionName() { return version == null ? null : version.primaryName(); }
        public boolean nonDefaultVersion() { return version != null && version.nonDefault; }
        public boolean isDefined() { return !isUndefined(); }
    }

    public static final class Relocation {
        public final int sectionIndex;
        public final long offset;
        public final long type;
        public final long addend;
        public final int symbolIndex;
        public final Symbol symbol;
        public final boolean copyReloc;

        public Relocation(int sectionIndex, long offset, long type, long addend, int symbolIndex,
                          Symbol symbol, boolean copyReloc) {
            this.sectionIndex = sectionIndex; this.offset = offset; this.type = type;
            this.addend = addend; this.symbolIndex = symbolIndex;
            this.symbol = symbol; this.copyReloc = copyReloc;
        }
    }

    public final int elfClass;      // 1 = 32bit, 2 = 64bit
    public final boolean bigEndian;
    public final int machine;
    public final Kind kind;
    public final String soname;
    public final List<String> needed = new ArrayList<>();
    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();
    public final List<Relocation> relocations = new ArrayList<>();
    public final List<VersionEntity> versions = new ArrayList<>();

    ElfModel(int elfClass, boolean bigEndian, int machine, Kind kind, String soname) {
        this.elfClass = elfClass; this.bigEndian = bigEndian; this.machine = machine;
        this.kind = kind; this.soname = soname;
    }

    public Section section(int idx) {
        if (idx < 0 || idx >= sections.size()) return null;
        return sections.get(idx);
    }
}
