package tribunal.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the Unix ar(5) format used by static libraries.
 *
 * The reader never extracts members eagerly: it records header positions so the
 * tribunal can perform lazy, reference-driven extraction (including the fixpoint
 * rescan needed for archive cycles).
 */
public final class ArchiveReader {

    public static final String MAGIC = "!<arch>\n";

    public record Member(String name, long dataOffset, long size) {}

    private final byte[] b;
    private final String archiveName;

    public ArchiveReader(byte[] bytes, String archiveName) {
        this.b = bytes;
        this.archiveName = archiveName;
    }

    public List<Member> members() {
        if (b.length < MAGIC.length()
                || !new String(b, 0, MAGIC.length(), StandardCharsets.US_ASCII).equals(MAGIC)) {
            throw new ElfFormatException("bad ar magic");
        }
        Map<Long, String> gnuLongNames = new HashMap<>();
        List<Member> candidates = new ArrayList<>();
        long pos = MAGIC.length();
        while (pos < b.length) {
            if (pos + 60 > b.length) throw new ElfFormatException("truncated ar header at " + pos);
            String rawName = ascii(pos, 16);
            long size = parseDecimal(pos + 48, 10, "ar member size");
            long data = pos + 60;
            if (data < 0 || size < 0 || data + size > b.length) {
                throw new ElfFormatException("ar member data past EOF");
            }
            String trimmed = rawName.trim();
            if (trimmed.equals("//")) {
                parseGnuLongNames(data, size, gnuLongNames);
            } else if (!trimmed.equals("/") && !trimmed.equals("/SYM64/")
                    && !trimmed.equals("/SYM64") && !trimmed.startsWith("/SYM64/")) {
                candidates.add(new Member(rawName, data, size));
            }
            long step = 60 + size + (size & 1);
            long next = pos + step;
            if (next > b.length) next = pos + 60 + size;
            if (next > b.length) throw new ElfFormatException("ar member padding past EOF");
            pos = next;
        }
        List<Member> out = new ArrayList<>();
        for (Member m : candidates) {
            String raw = m.name();
            String name;
            long dataOffset = m.dataOffset();
            long size = m.size();
            String t = raw.trim();
            if (t.startsWith("/") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
                long off = Long.parseLong(t.substring(1).trim());
                name = gnuLongNames.get(off);
                if (name == null) throw new ElfFormatException("missing GNU long name at offset " + off);
            } else if (t.startsWith("#1/")) {
                int inlineLen = Integer.parseInt(t.substring(3));
                if (inlineLen > size) throw new ElfFormatException("BSD long name too long");
                int end = 0;
                while (end < inlineLen && b[(int) (dataOffset + end)] != 0) end++;
                name = ascii(dataOffset, end);
                dataOffset += inlineLen;
                size -= inlineLen;
            } else {
                int end = 0;
                while (end < raw.length() && raw.charAt(end) != ' ' && raw.charAt(end) != '/') end++;
                name = raw.substring(0, end);
            }
            out.add(new Member(name, dataOffset, size));
        }
        return out;
    }

    public ElfObject parseMember(Member m) {
        if (m.dataOffset() + m.size() > b.length) {
            throw new ElfFormatException("member " + m.name() + " data OOR");
        }
        byte[] data = new byte[(int) m.size()];
        System.arraycopy(b, (int) m.dataOffset(), data, 0, (int) m.size());
        ElfObject o = new ElfReader(data).parse();
        o.fromArchive = true;
        o.archiveName = archiveName;
        o.memberName = m.name();
        o.origin = archiveName + "(" + m.name() + ")";
        return o;
    }

    private void parseGnuLongNames(long data, long size, Map<Long, String> out) {
        long end = data + size;
        long runStart = data;
        long runIndex = 0;
        for (long p = data; p < end; p++) {
            byte c = b[(int) p];
            if (c == '\n' || c == 0) {
                long stop = p;
                if (stop > runStart && b[(int) (stop - 1)] == '/') stop--;
                String name = new String(b, (int) runStart, (int) (stop - runStart),
                        StandardCharsets.US_ASCII);
                out.put(runIndex, name);
                long consumed = p - data - runIndex + 1;
                runIndex += consumed;
                runStart = p + 1;
            }
        }
    }

    private String ascii(long off, int len) {
        return new String(b, (int) off, len, StandardCharsets.US_ASCII);
    }

    private long parseDecimal(int off, int len, String what) {
        long v = 0;
        boolean any = false;
        for (int i = 0; i < len; i++) {
            char c = (char) (b[off + i] & 0xff);
            if (c == ' ' || c == '`') continue;
            if (c < '0' || c > '9') throw new ElfFormatException("bad " + what + " near '" + c + "'");
            v = v * 10 + (c - '0');
            any = true;
        }
        if (!any) throw new ElfFormatException("missing " + what);
        return v;
    }
}
