package com.example.tribunal.core.elf;

import java.util.List;

public final class ElfModels {
    private ElfModels() {}

    public enum ElfClass { ELF32, ELF64 }
    public enum ElfType { RELOCATABLE, EXECUTABLE, SHARED, CORE, OTHER }
    public enum Binding { LOCAL, GLOBAL, WEAK, OTHER }
    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED, OTHER }
    public enum SymbolKind { NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, IFUNC, OTHER }

    public record Section(int index, String name, int type, long flags, long address, long offset, long size,
                          int link, int info, long alignment, long entrySize) {}

    public record ElfSymbol(int index, String name, long value, long size, Binding binding, SymbolKind kind,
                            Visibility visibility, int sectionIndex, String sectionName, short sectionType,
                            String versionName, boolean versionDefault, boolean versionDefinedHere) {}

    public record Relocation(int index, int sectionIndex, String sectionName, long offset, int type, int symbolIndex,
                             long addend, String symbolName, boolean copy, boolean pcrel) {}

    public record Group(int sectionIndex, String signature, boolean comdat, List<Integer> members) {}

    public record VersionDefinition(String name, int hash, int flags, List<String> aliases) {}
    public record VersionRequirement(String fileName, List<RequestedVersion> versions) {}
    public record RequestedVersion(String name, int hash, List<String> aliases) {}

    public record VersionInfo(List<VersionDefinition> definitions, List<VersionRequirement> requirements,
                              List<Short> symbolVersions) {}

    public record ElfObject(String displayName, byte[] bytes, ElfClass elfClass, ElfType type, boolean littleEndian,
                            int machine, List<Section> sections, List<ElfSymbol> symbols,
                            List<Relocation> relocations, List<Group> groups, List<String> needed,
                            String soname, VersionInfo versions) {}

    public record ArchiveMember(String name, byte[] bytes) {}

    public static boolean definedSection(ElfSymbol symbol) {
        int section = symbol.sectionIndex();
        return section > 0 && section < 0xFF00;
    }
}
