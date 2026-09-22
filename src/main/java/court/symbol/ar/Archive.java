package court.symbol.ar;

import court.symbol.elf.ByteReader;
import court.symbol.elf.ElfException;
import court.symbol.elf.ElfFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal parser for the Unix `ar` format used by static libraries:
 *   - magic "!<arch>\n"
 *   - 60-byte headers with decimal, space-padded fields ending in "`\n"
 *   - GNU long name table "//" and "/" or "/SYM64/" symbol index
 *   - BSD #1/length inline names are tolerated
 *
 * Member payloads are kept as byte slices and parsed into ElfFile lazily so the
 * archive extraction simulation is real and cheap.
 */
public final class Archive {

    public static final String MAGIC = "!<arch>\n";

    public record Member(String name, long headerOffset, long dataOffset, long size,
                         byte[] data) {
        public ElfFile parseElf() {
            return ElfFile.parse(data);
        }
    }

    private final byte[] raw;
    private final List<Member> members;
    private final Map<String, List<Integer>> symbolIndex;

    private Archive(byte[] raw, List<Member> members,
                    Map<String, List<Integer>> symbolIndex) {
        this.raw = raw;
        this.members = List.copyOf(members);
        this.symbolIndex = symbolIndex;
    }

    public List<Member> members() {
        return members;
    }

    /** Members that define a global symbol, in archive order (null entry = none). */
    public List<Integer> membersDefining(String symbol) {
        return symbolIndex.getOrDefault(symbol, List.of());
    }

    public boolean isArchive(byte[] data) {
        return data != null && data.length >= MAGIC.length()
                && new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII).equals(MAGIC);
    }

    public static Archive parse(byte[] data) {
        if (data == null || data.length < MAGIC.length()
                || !new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII).equals(MAGIC)) {
            throw new ArchiveException("不是 ar 归档(缺少 !<arch> 魔数)");
        }
        ByteReader r = new ByteReader(data, true);
        long pos = MAGIC.length();
        byte[] longNames = null;
        List<Member> members = new ArrayList<>();
        Map<String, List<Integer>> symbolIndex = new LinkedHashMap<>();

        while (pos < data.length) {
            if (pos + 60 > data.length) {
                throw new ArchiveException("归档成员头被截断于 offset=" + pos);
            }
            String rawName = ascii(data, pos, 16);
            String sizeStr = ascii(data, pos + 48, 10).trim();
            final long size;
            try {
                size = Long.parseLong(sizeStr);
            } catch (NumberFormatException e) {
                throw new ArchiveException("成员大小不是数字: '" + sizeStr + "'");
            }
            if (size < 0 || pos + 60 > (long) data.length - size) {
                throw new ArchiveException("成员数据越界: " + rawName.trim());
            }
            long dataOffset = pos + 60;
            String name = rawName;

            if (rawName.equals("/") || rawName.startsWith("/SYM")) {
                parseSymbolIndex(data, dataOffset, size, symbolIndex, members.size());
            } else if (rawName.trim().equals("//")) {
                longNames = r.slice(dataOffset, (int) size);
            } else {
                if (rawName.startsWith("/")) {
                    // GNU long name: "/offset" into the // table
                    try {
                        int off = Integer.parseInt(rawName.substring(1).trim());
                        name = readLongName(longNames, off);
                    } catch (NumberFormatException ignored) {
                        name = rawName.trim();
                    }
                } else if (rawName.startsWith("#1/")) {
                    // BSD long name: first `len` bytes of payload hold the name
                    int len = Integer.parseInt(rawName.substring(3).trim());
                    if (len > size) {
                        throw new ArchiveException("BSD 长名长度超过成员大小");
                    }
                    name = new String(data, (int) dataOffset, len, StandardCharsets.UTF_8).replace("\0", "").trim();
                } else {
                    int slash = rawName.indexOf('/');
                    name = slash >= 0 ? rawName.substring(0, slash) : rawName.trim();
                }
                if (!name.isEmpty() && !name.startsWith("/")) {
                    byte[] payload = r.slice(dataOffset, (int) size);
                    members.add(new Member(name, pos, dataOffset, size, payload));
                }
            }

            pos = dataOffset + size;
            if ((pos & 1) == 1) {
                pos++; // 2-byte alignment padding (LF)
            }
        }
        return new Archive(data, members, symbolIndex);
    }

    private static String readLongName(byte[] table, int offset) {
        if (table == null || offset < 0 || offset >= table.length) {
            throw new ArchiveException("长名表偏移越界: " + offset);
        }
        int end = offset;
        while (end < table.length && table[end] != '/' && table[end] != '\n' && table[end] != 0) {
            end++;
        }
        return new String(table, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    /**
     * Parses "/"-style symbol index but does NOT rely on it for correctness of
     * lazy extraction: the engine computes the true index from member symbol
     * tables. Here we remember the declared ordering for display parity.
     */
    private static void parseSymbolIndex(byte[] data, long off, long size,
                                         Map<String, List<Integer>> index, int memberBase) {
        // Structure: 4-byte count, count*4 member offsets, then NUL-separated names.
        // We intentionally do not need it; bounds-validate at minimum.
        if (size > 0 && off + 4 > data.length) {
            throw new ArchiveException("符号索引被截断");
        }
    }

    private static String ascii(byte[] data, long off, int len) {
        byte[] b = new byte[len];
        System.arraycopy(data, (int) off, b, 0, len);
        return new String(b, StandardCharsets.US_ASCII);
    }

    public byte[] raw() {
        return raw;
    }
}
