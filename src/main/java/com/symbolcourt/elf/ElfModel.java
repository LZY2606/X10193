package com.symbolcourt.elf;

import java.util.List;
import java.util.Map;

/** Parsed ELF model. All collections are immutable snapshots produced by {@link ElfParser}. */
public final class ElfModel {

    public enum ObjectKind { REL, DYN, EXEC, CORE, UNKNOWN }

    /** Symbol binding (STB_*). */
    public static final int BIND_LOCAL = 0, BIND_GLOBAL = 1, BIND_WEAK = 2;
    /** Symbol type (STT_*). */
    public static final int TYPE_NOTYPE = 0, TYPE_OBJECT = 1, TYPE_FUNC = 2, TYPE_COMMON = 5;
    /** Visibility (STV_*). */
    public static final int VIS_DEFAULT = 0, VIS_INTERNAL = 1, VIS_HIDDEN = 2, VIS_PROTECTED = 3;
    /** Special section indexes. */
    public static final int SHN_UNDEF = 0, SHN_COMMON = 0xfff2, SHN_ABS = 0xfff1;

    public record Symbol(
            String name,
            int bind,
            int type,
            int visibility,
            int shndx,
            long value,
            long size,
            int versionIndex,      // raw index from .gnu.version (0 = none), dynsym only
            String versionName,    // restored version name, null when unversioned
            boolean versionHidden, // non-default version ("@" instead of "@@")
            boolean dynamic,       // came from .dynsym rather than .symtab
            String groupSignature  // COMDAT group signature of the defining section, null if none
    ) {
        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON || type == TYPE_COMMON; }
        public boolean isLocal() { return bind == BIND_LOCAL; }
        public boolean isWeak() { return bind == BIND_WEAK; }
        public boolean isHidden() { return visibility == VIS_HIDDEN || visibility == VIS_INTERNAL; }
        public boolean isProtected() { return visibility == VIS_PROTECTED; }

        /** Identity used by the adjudicator: name plus version when versioned. */
        public String versionedName() {
            if (versionName == null) return name;
            return name + (versionHidden ? "@" : "@@") + versionName;
        }
    }

    public record Relocation(
            String sectionName, // section the relocation applies to
            long offset,
            long type,
            int symbolIndex,    // index into the linked symbol table
            String symbolName,  // resolved symbol name ("" for section symbols w/o name)
            long addend,
            boolean dynamic
    ) {}

    public record VersionDef(String name, int index, boolean hidden) {}

    public record VersionNeed(String library, String name, int index, boolean hidden) {}

    public record SectionGroup(String signature, List<String> memberSections) {}

    public record ElfFile(
            String name,
            ObjectKind kind,
            boolean elf64,
            List<Symbol> symbols,          // .symtab (may be empty for stripped DYN)
            List<Symbol> dynamicSymbols,   // .dynsym
            List<Relocation> relocations,
            List<VersionDef> versionDefs,
            List<VersionNeed> versionNeeds,
            List<String> needed,           // DT_NEEDED
            List<SectionGroup> groups
    ) {
        /** Symbols participating in inter-object resolution for this object kind. */
        public List<Symbol> linkSymbols() {
            if (kind == ObjectKind.DYN && !dynamicSymbols.isEmpty()) return dynamicSymbols;
            return !symbols.isEmpty() ? symbols : dynamicSymbols;
        }
    }

    /** A parsed ar archive: thin members parsed eagerly, corrupt members isolated. */
    public record ArchiveFile(
            String name,
            List<ElfFile> members,
            List<String> corruptMembers   // "memberName: error" entries, isolated
    ) {}
}
