package court.symbol.elf;

import java.util.ArrayList;
import java.util.List;

public class ParsedBinary {
    public enum Format { ELF_OBJECT, ELF_SHARED, ELF_EXECUTABLE, ARCHIVE }

    public Format format;
    public String fileName;
    public String soname = "";
    public boolean elf64;
    public boolean littleEndian = true;
    public int machine;
    public int elfType;
    public final List<ElfSection> sections = new ArrayList<>();
    public final List<ElfSymbol> symbols = new ArrayList<>();
    public final List<ElfSymbol> staticSymbols = new ArrayList<>();
    public final List<ElfSymbol> dynamicSymbols = new ArrayList<>();
    public final List<ElfRelocation> relocations = new ArrayList<>();
    public final List<VersionDefinition> versionDefinitions = new ArrayList<>();
    public final List<VersionNeed> versionNeeds = new ArrayList<>();
    public final List<SectionGroup> groups = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public final List<ParsedBinary> archiveMembers = new ArrayList<>();
    public final List<String> memberErrors = new ArrayList<>();
    public String archiveMemberName = "";
    public long dataOffset;
    public byte[] data;

    public boolean isArchive() {
        return format == Format.ARCHIVE;
    }

    public boolean isRelocatable() {
        return format == Format.ELF_OBJECT;
    }

    public boolean isDynamicComponent() {
        return format == Format.ELF_SHARED || format == Format.ELF_EXECUTABLE;
    }
}
