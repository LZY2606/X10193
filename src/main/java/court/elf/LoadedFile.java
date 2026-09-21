package court.elf;

import java.util.ArrayList;
import java.util.List;

/**
 * One imported input in explicit load order. An input is either a single ELF file
 * (REL / DYN / EXEC) or an ar archive whose members are parsed lazily. Corruption is
 * scoped to the offending input or member only.
 */
public class LoadedFile {
    public int order;
    public String fileName;
    public byte[] raw;
    public String kind;             // ELF | ARCHIVE | CORRUPT
    public boolean corrupt;
    public String error;

    public ElfImage image;                                  // when kind == ELF
    public ArReader.Archive archive;                        // when kind == ARCHIVE
    public List<LoadedBinary> members = new ArrayList<>();  // archive members (parsed on demand)

    public LoadedBinary standalone() {
        LoadedBinary b = new LoadedBinary();
        b.sourceFile = fileName;
        b.image = image;
        b.corrupt = corrupt;
        b.error = error;
        b.unitId = fileName;
        return b;
    }
}
