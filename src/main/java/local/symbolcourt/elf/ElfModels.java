package local.symbolcourt.elf;

import java.util.List;

public final class ElfModels {
    private ElfModels() {}

    public enum Binding { LOCAL, GLOBAL, WEAK, OTHER }
    public enum SymbolType { NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, OTHER }
    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED, OTHER }
    public enum BinaryKind { RELOCATABLE, SHARED, EXECUTABLE, CORE, UNKNOWN, ARCHIVE, CORRUPT }

    public record VersionInfo(int index, String name, boolean defined, boolean required,
                              boolean nonDefault, String dependencyFile) {
        public String qualifiedName(String symbol) {
            return symbol + (name == null || name.isBlank() ? "" : "@" + (nonDefault ? "@" : "") + name);
        }
    }

    public record Section(int index, String name, int type, long flags, long address,
                          long offset, long size, int link, int info,
                          long alignment, long entrySize, int groupIndex, String groupSignature) {}

    public record ElfSymbol(int index, String name, long value, long size,
                            Binding binding, SymbolType type, Visibility visibility,
                            int sectionIndex, boolean defined, boolean common,
                            String sectionName, String comdatSignature,
                            VersionInfo version) {
        public boolean globalLike() {
            return binding != Binding.LOCAL;
        }

        public String qualifiedName() {
            return version == null ? name : version.qualifiedName(name);
        }
    }

    public record Relocation(int sectionIndex, String sectionName, long offset,
                             long addend, int type, long symbolIndex,
                             String symbolName, String symbolVersion, boolean copy) {}

    public record VersionEdge(String from, String to, String kind, String label) {}

    public record ParsedBinary(String name, BinaryKind kind, boolean elf64, boolean littleEndian,
                               int machine, String soname, List<Section> sections,
                               List<ElfSymbol> symbols, List<Relocation> relocations,
                               List<String> needed, List<VersionInfo> versions,
                               List<VersionEdge> versionEdges, String error) {
        public boolean usable() {
            return error == null && kind != BinaryKind.CORRUPT;
        }
    }

    public record ArchiveMember(String memberName, long memberIndex, byte[] content, ParsedBinary parsed) {}

    public record LoadedFile(String name, byte[] content, String mediaType,
                             ParsedBinary parsed, List<ArchiveMember> members, String error) {
        public boolean archive() {
            return parsed != null && parsed.kind == BinaryKind.ARCHIVE;
        }
    }
}
