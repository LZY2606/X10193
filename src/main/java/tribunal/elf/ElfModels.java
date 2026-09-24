package tribunal.elf;

import java.util.ArrayList;
import java.util.List;

public final class ElfModels {
    private ElfModels() {}

    public enum Binding { LOCAL, GLOBAL, WEAK, GNU_UNIQUE, OTHER }
    public enum SymbolType { NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, OTHER }
    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED, OTHER }
    public enum SymbolKind { DEFINED, UNDEFINED, COMMON, COPY }

    public record Section(int index, String name, int type, long flags, long address, long offset, long size,
                          int link, int info, long entrySize) {}

    public record VersionName(int index, String name, boolean nonDefault, boolean weakVersion,
                              boolean definition, String neededLibrary) {}

    public record SectionGroup(String signature, boolean comdat, List<Integer> sectionIndices, int sectionIndex) {}

    public record ElfSymbol(int index, int symbolTableIndex, String name, Binding binding, SymbolType type,
                            Visibility visibility, int sectionIndex, String sectionName, long value, long size,
                            SymbolKind kind, Integer versionIndex, String versionName, boolean nonDefaultVersion,
                            boolean weakVersion, String groupSignature, boolean discardedGroup) {
        public boolean isNamedLinkSymbol() {
            return name != null && !name.isBlank() && type != SymbolType.SECTION && type != SymbolType.FILE;
        }
        public boolean globalScope() {
            return binding == Binding.GLOBAL || binding == Binding.WEAK || binding == Binding.GNU_UNIQUE;
        }
    }

    public record Relocation(int sectionIndex, String sectionName, long offset, int type, long rawType,
                             int symbolIndex, int symbolTableIndex, String symbolName, long addend,
                             boolean hasAddend, String kind) {}

    public record ParsedObject(String objectKey, String displayName, String archiveName, String memberName,
                               boolean elf, boolean valid, String error, int elfType, int machine,
                               boolean elf64, boolean littleEndian, List<Section> sections,
                               List<ElfSymbol> symbols, List<VersionName> versions, List<SectionGroup> groups,
                               List<Relocation> relocations, String soname, List<String> neededLibraries) {
        public ParsedObject {
            sections = sections == null ? List.of() : List.copyOf(sections);
            symbols = symbols == null ? List.of() : List.copyOf(symbols);
            versions = versions == null ? List.of() : List.copyOf(versions);
            groups = groups == null ? List.of() : List.copyOf(groups);
            relocations = relocations == null ? List.of() : List.copyOf(relocations);
            neededLibraries = neededLibraries == null ? List.of() : List.copyOf(neededLibraries);
        }

        public String uniqueName() {
            return archiveName == null ? displayName : archiveName + "(" + memberName + ")";
        }

        public static ParsedObject invalid(String objectKey, String displayName, String archiveName,
                                           String memberName, String error) {
            return new ParsedObject(objectKey, displayName, archiveName, memberName, false, false, error,
                    0, 0, true, true, List.of(), List.of(), List.of(), List.of(), List.of(), null,
                    new ArrayList<>());
        }
    }

    public record ArchiveMember(String name, long dataOffset, long dataSize, byte[] data) {}

    public record ParsedArchive(String objectKey, String displayName, boolean valid, String error,
                                List<ArchiveMember> members, java.util.Map<String, List<String>> symbolTable) {
        public ParsedArchive {
            members = members == null ? List.of() : List.copyOf(members);
            symbolTable = symbolTable == null ? Map.of() : Map.copyOf(symbolTable);
        }
    }
}
