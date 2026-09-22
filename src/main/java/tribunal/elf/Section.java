package tribunal.elf;

public record Section(
        int index,
        int type,
        long flags,
        long address,
        long offset,
        long size,
        int link,
        int info,
        long alignment,
        long entrySize,
        String name
) {
    public boolean alloc() { return (flags & Elf.SHF_ALLOC) != 0; }
}
