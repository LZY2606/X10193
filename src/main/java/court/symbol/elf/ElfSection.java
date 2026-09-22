package court.symbol.elf;

public class ElfSection {
    public final int index;
    public final int nameOffset;
    public String name;
    public final int type;
    public final long flags;
    public final long address;
    public final long offset;
    public final long size;
    public final int link;
    public final int info;
    public final long entrySize;
    public String groupSignature;

    public ElfSection(int index, int nameOffset, String name, int type, long flags, long address, long offset, long size,
                      int link, int info, long entrySize) {
        this.index = index;
        this.nameOffset = nameOffset;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.address = address;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.entrySize = entrySize;
    }
}
