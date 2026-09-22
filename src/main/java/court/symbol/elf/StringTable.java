package court.symbol.elf;

/**
 * Lazily read ELF string table (.strtab / .dynstr / .shstrtab), always
 * bounded by the owning section's end offset.
 */
public final class StringTable {

    private final ByteReader reader;
    private final long offset;
    private final long end;

    private StringTable(ByteReader reader, long offset, long end) {
        this.reader = reader;
        this.offset = offset;
        this.end = end;
    }

    public static StringTable of(ByteReader reader, Elf.Section section) {
        if (section == null) {
            return new StringTable(reader, 0, 0);
        }
        long start = section.offset();
        long limit = Math.min(reader.size(), start + section.size());
        if (start > reader.size()) {
            throw new ElfException("字符串表节区越界");
        }
        return new StringTable(reader, start, limit);
    }

    public String at(int index) {
        if (index < 0) {
            throw new ElfException("字符串表索引为负: " + index);
        }
        long pos = offset + index;
        if (offset == end || pos >= end) {
            return "";
        }
        return reader.cstr(pos, end);
    }
}
