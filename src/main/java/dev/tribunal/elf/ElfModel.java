package dev.tribunal.elf;

import java.util.List;

/** Parsed representation of an ELF object (64-bit little endian only). */
public final class ElfModel {
    public enum FileKind { RELOCATABLE, SHARED, EXECUTABLE, OTHER }

    public String origin;
    public String displayName;
    public boolean isArchiveMember;
    public String archivePath;
    public FileKind kind = FileKind.OTHER;
    public int machine;
    public List<ElfSection> sections = List.of();
    public List<ElfSymbol> symbols = List.of();
    public List<VersionDef> versionDefs = List.of();
    public List<VersionNeed> versionNeeds = List.of();
    public List<Relocation> relocations = List.of();
    public List<Group> groups = List.of();
    public List<String> neededLibraries = List.of();
    public String soname;
    public List<ParseProblem> problems = List.of();

    public void problem(String message) {
        problems.add(new ParseProblem(message));
    }

    public ElfSection sectionByIndex(int idx) {
        if (idx < 0 || idx >= sections.size()) return null;
        return sections.get(idx);
    }

    public record ParseProblem(String message) {}

    public record ElfSection(int index, String name, long type, long flags, long addr, long offset,
                             long size, long link, long info, long addralign, long entsize) {
        public boolean isSHT(int type) { return this.type == type; }
    }

    public record ElfSymbol(int index, String name, long info, long other, int shndx, long value,
                            long size, int versionIndex, boolean dynamic) {
        public int bind() { return (int) (info >> 4) & 0xf; }
        public int type() { return (int) (info & 0xf); }
        public int visibility() { return (int) (other & 0x3); }
    }

    public record VersionEntry(String name, boolean hidden) {}

    public record VersionDef(int index, long flags, List<String> names) {
        public String baseName() { return names.isEmpty() ? null : names.get(0); }
    }

    public record VersionNeedAux(String name, boolean hidden, long hash, int index) {}

    public record VersionNeed(int index, String fileName, List<VersionNeedAux> entries) {}

    public record Relocation(long offset, long type, long symbolIndex, long addend, String sectionName,
                             boolean addendForm, int symTableLink) {
        public boolean isCopyReloc(long machine) { return machine == 62 && type == ElfConstants.R_X86_64_COPY; }

        public ElfSymbol resolveSymbol(ElfModel model) {
            int si = (int) symbolIndex;
            if (symTableLink >= 0 && symTableLink < model.sections.size() && model.sections.get(symTableLink).isSHT(ElfConstants.SHT_DYNSYM)) {
                for (ElfSymbol sym : model.symbols) {
                    if (sym.index() == si && sym.dynamic()) return sym;
                }
            }
            if (si >= 0 && si < model.symbols.size()) {
                ElfSymbol sym = model.symbols.get(si);
                if (!sym.dynamic()) return sym;
            }
            for (ElfSymbol sym : model.symbols) {
                if (sym.index() == si && !sym.dynamic()) return sym;
            }
            return null;
        }
    }

    public record Group(int sectionIndex, String signature, boolean comdat, List<Integer> memberSections) {}
}
