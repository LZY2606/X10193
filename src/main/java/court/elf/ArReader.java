package court.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decoder for the GNU/SysV {@code ar} archive format: symbol lookup table ("/"),
 * 64-bit table ("/SYM64/"), long filename table ("//") and regular members.
 * Members stay raw byte ranges so ELF parsing is lazy and a corrupt member is
 * isolated without affecting its siblings.
 */
public final class ArReader {
    public static final String MAGIC = "!<arch>\n";

    public static final class Member {
        public String name;
        public long offset;
        public long size;
        public int order;
    }

    public static final class Archive {
        public boolean valid;
        public String error;
        public final List<Member> members = new ArrayList<>();
        public final Map<String, List<Integer>> symbolToMember = new LinkedHashMap<>();
    }

    private static final class RawSym {
        final String name;
        final long memberOffset;
        RawSym(String name, long memberOffset) { this.name = name; this.memberOffset = memberOffset; }
    }

    private ArReader() {}

    public static boolean looksLikeArchive(byte[] data) {
        return data != null && data.length >= MAGIC.length()
                && new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII).equals(MAGIC);
    }

    public static Archive readIndex(byte[] data) {
        Archive ar = new Archive();
        List<RawSym> rawSyms = new ArrayList<>();
        try {
            if (!looksLikeArchive(data)) throw new IllegalArgumentException("not an ar archive");
            String longNames = null;
            long pos = MAGIC.length();
            while (pos + 60 <= data.length) {
                String rawName = new String(data, (int) pos, 16, StandardCharsets.US_ASCII);
                long size = parseFixed(data, (int) pos + 48, 10);
                long dataStart = pos + 60;
                if (dataStart + size > data.length) throw new IllegalArgumentException("member data past end");

                String trimmed = rawName.trim();
                if (trimmed.equals("/") || trimmed.startsWith("/SYM64")) {
                    rawSyms.addAll(parseSymbolTable(data, dataStart, size));
                } else if (trimmed.equals("//")) {
                    longNames = new String(data, (int) dataStart, (int) size, StandardCharsets.UTF_8);
                } else {
                    Member m = new Member();
                    m.offset = dataStart;
                    m.size = size;
                    if (rawName.startsWith("/")) {
                        m.name = resolveLongName(longNames, Integer.parseInt(rawName.substring(1).trim()));
                    } else {
                        int slash = rawName.indexOf('/');
                        m.name = slash >= 0 ? rawName.substring(0, slash) : rawName.trim();
                    }
                    m.order = ar.members.size();
                    ar.members.add(m);
                }
                pos = dataStart + size;
                if ((size & 1) == 1) pos++;
            }
            for (RawSym rs : rawSyms) {
                int mi = findMember(ar, rs.memberOffset);
                if (mi >= 0) ar.symbolToMember.computeIfAbsent(rs.name, k -> new ArrayList<>()).add(mi);
            }
            ar.valid = true;
        } catch (Throwable t) {
            ar.valid = false;
            ar.error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        return ar;
    }

    public static byte[] memberData(byte[] data, Member m) {
        byte[] out = new byte[(int) m.size];
        System.arraycopy(data, (int) m.offset, out, 0, (int) m.size);
        return out;
    }

    private static int findMember(Archive ar, long off) {
        for (Member m : ar.members) if (m.offset == off) return m.order;
        return -1;
    }

    private static String resolveLongName(String table, int offset) {
        if (table == null) throw new IllegalArgumentException("long name used without // table");
        int end = table.indexOf('\n', offset);
        if (end < 0) end = table.length();
        String n = table.substring(offset, end).trim();
        if (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        return n;
    }

    private static long parseFixed(byte[] d, int off, int len) {
        String s = new String(d, off, len, StandardCharsets.US_ASCII).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty numeric ar field");
        return Long.parseLong(s);
    }

    private static List<RawSym> parseSymbolTable(byte[] data, long start, long size) {
        List<RawSym> out = new ArrayList<>();
        int p = (int) start;
        int count = read4(data, p);
        if (count < 0 || count > 1_000_000 || 4L + (long) count * 4 > size) {
            throw new IllegalArgumentException("insane archive symbol count " + count);
        }
        p += 4;
        long[] offsets = new long[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = read4(data, p) & 0xffffffffL;
            p += 4;
        }
        for (int i = 0; i < count; i++) {
            int e = p;
            long limit = start + size;
            while (e < limit && data[e] != 0) e++;
            if (e > limit) throw new IllegalArgumentException("truncated archive symbol name");
            out.add(new RawSym(new String(data, p, e - p, StandardCharsets.UTF_8), offsets[i]));
            p = e + 1;
        }
        return out;
    }

    private static int read4(byte[] d, int off) {
        return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8)
                | ((d[off + 2] & 0xff) << 16) | ((d[off + 3] & 0xff) << 24);
    }
}
