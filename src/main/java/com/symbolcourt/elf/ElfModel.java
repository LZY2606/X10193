package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** In-memory model of one parsed ELF object (ET_REL / ET_DYN / ET_EXEC). */
public final class ElfModel {

    public static final int ET_REL = 1;
    public static final int ET_EXEC = 2;
    public static final int ET_DYN = 3;

    public static final int SHT_RELA = 4;
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_DYNAMIC = 6;
    public static final int SHT_REL = 9;
    public static final int SHT_DYNSYM = 11;
    public static final int SHT_GROUP = 17;
    public static final int SHT_GNU_VERDEF = 0x6ffffffd;
    public static final int SHT_GNU_VERNEED = 0x6ffffffe;
    public static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static final int SHN_UNDEF = 0;
    public static final int SHN_COMMON = 0xfff2;
    public static final int SHN_ABS = 0xfff1;

    public static final int STB_LOCAL = 0;
    public static final int STB_GLOBAL = 1;
    public static final int STB_WEAK = 2;

    public static final int STT_NOTYPE = 0;
    public static final int STT_OBJECT = 1;
    public static final int STT_FUNC = 2;

    public static final int STV_DEFAULT = 0;
    public static final int STV_INTERNAL = 1;
    public static final int STV_HIDDEN = 2;
    public static final int STV_PROTECTED = 3;

    public static final int GRP_COMDAT = 1;
    public static final int VERSYM_HIDDEN = 0x8000;
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    public record Section(String name, int type, long flags, long addr, long offset, long size,
                          int link, int info, long addralign, long entsize, int index) {}

    public record Sym(String name, int bind, int type, int visibility, int shndx,
                      long value, long size, int index, boolean dynamic) {
        public boolean isUndefined() { return shndx == SHN_UNDEF; }
        public boolean isCommon() { return shndx == SHN_COMMON; }
        public boolean isDefined() { return shndx != SHN_UNDEF; }
        public String bindName() {
            return switch (bind) { case STB_LOCAL -> "LOCAL"; case STB_GLOBAL -> "GLOBAL"; case STB_WEAK -> "WEAK"; default -> "?" + bind; };
        }
        public String visibilityName() {
            return switch (visibility) {
                case STV_DEFAULT -> "DEFAULT"; case STV_INTERNAL -> "INTERNAL";
                case STV_HIDDEN -> "HIDDEN"; case STV_PROTECTED -> "PROTECTED";
                default -> "?" + visibility;
            };
        }
        public String typeName() {
            return switch (type) { case STT_NOTYPE -> "NOTYPE"; case STT_OBJECT -> "OBJECT"; case STT_FUNC -> "FUNC"; default -> "?" + type; };
        }
    }

    public record Reloc(long offset, long type, int symIndex, long addend, boolean hasAddend, int targetSection) {}

    public record VersionDef(int ndx, int flags, String name) {}

    public record Vernaux(int other, int flags, String name) {}

    public record VersionNeed(String file, List<Vernaux> aux) {}

    public record GroupInfo(String signature, boolean comdat, List<Integer> memberSections) {}

    /** Resolved version request/definition attached to one dynamic symbol. */
    public record VersionRef(String name, boolean hidden) {}

    public int elfType;
    public String elfTypeName() {
        return switch (elfType) { case ET_REL -> "ET_REL"; case ET_EXEC -> "ET_EXEC"; case ET_DYN -> "ET_DYN"; default -> "?" + elfType; };
    }

    public final List<Section> sections = new ArrayList<>();
    public final List<Sym> symtab = new ArrayList<>();
    public final List<Sym> dynsym = new ArrayList<>();
    public int[] versym = new int[0];
    public final List<VersionDef> verdefs = new ArrayList<>();
    public final List<VersionNeed> verneeds = new ArrayList<>();
    public final List<Reloc> relocs = new ArrayList<>();
    public final List<String> dtNeeded = new ArrayList<>();
    public final List<GroupInfo> groups = new ArrayList<>();

    /** Resolve the version attached to a dynamic symbol index, or null when unversioned. */
    public VersionRef versionOf(int dynSymIndex) {
        if (dynSymIndex < 0 || dynSymIndex >= versym.length) return null;
        int raw = versym[dynSymIndex];
        boolean hidden = (raw & VERSYM_HIDDEN) != 0;
        int ndx = raw & 0x7fff;
        if (ndx == VER_NDX_LOCAL || ndx == VER_NDX_GLOBAL) return null;
        for (VersionDef vd : verdefs) {
            if (vd.ndx() == ndx) return new VersionRef(vd.name(), hidden);
        }
        for (VersionNeed vn : verneeds) {
            for (Vernaux a : vn.aux()) {
                if (a.other() == ndx) return new VersionRef(a.name(), hidden);
            }
        }
        return new VersionRef("<bad-version-index:" + ndx + ">", hidden);
    }
}
