package tribunal.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** GNU ar 归档解析。仅按成员在归档中的实际出现顺序枚举，绝不使用文件系统遍历次序。 */
public final class ArchiveReader {

    public static final class Member {
        public String name;
        public int order;
        public byte[] data;
        public boolean corrupt;
        public String error;
        public transient ElfData elf;
    }

    public static final class Archive {
        public boolean corrupt;
        public String error;
        public final List<Member> members = new ArrayList<>();
    }

    private static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    private ArchiveReader() {}

    public static Archive parse(byte[] data) {
        Archive ar = new Archive();
        try {
            if (data.length < 8) throw new ElfFormatException("archive shorter than magic");
            for (int i = 0; i < 8; i++)
                if (data[i] != MAGIC[i]) throw new ElfFormatException("bad ar magic");
            long off = 8;
            byte[] longNames = null;
            int order = 0;
            while (off + 60 <= data.length) {
                String rawName = new String(data, (int) off, 16, StandardCharsets.US_ASCII).trim();
                String sizeStr = new String(data, (int) off + 48, 10, StandardCharsets.US_ASCII).trim();
                if (data[(int) off + 58] != 0x60 || data[(int) off + 59] != '\n')
                    throw new ElfFormatException("bad ar header terminator at " + off);
                long size;
                try { size = Long.parseLong(sizeStr); }
                catch (NumberFormatException ex) { throw new ElfFormatException("bad ar member size '" + sizeStr + "'"); }
                long content = off + 60;
                if (content < 0 || content > data.length || content + size > data.length)
                    throw new ElfFormatException("ar member extends beyond end of archive at " + off);
                if (rawName.equals("//")) {
                    longNames = new byte[(int) size];
                    System.arraycopy(data, (int) content, longNames, 0, (int) size);
                } else if (!rawName.equals("/") && !rawName.equals("/SYM64/")) {
                    Member m = new Member();
                    m.order = order++;
                    m.name = resolveName(rawName, longNames, data, content);
                    m.data = new byte[(int) size];
                    System.arraycopy(data, (int) content, m.data, 0, (int) size);
                    ar.members.add(m);
                }
                off = content + size + (size & 1L);
            }
            if (off != data.length)
                throw new ElfFormatException("trailing bytes after archive members: " + (data.length - off));
            return ar;
        } catch (ElfFormatException ex) {
            ar.corrupt = true;
            ar.error = ex.getMessage();
            return ar;
        } catch (RuntimeException ex) {
            ar.corrupt = true;
            ar.error = "malformed archive: " + ex.getMessage();
            return ar;
        }
    }

    private static String resolveName(String raw, byte[] longNames, byte[] data, long content) {
        if (raw.startsWith("#1/")) {
            int n = Integer.parseInt(raw.substring(3));
            if (n >= 0 && content + n <= data.length)
                return new String(data, (int) content, n, StandardCharsets.US_ASCII).replace("\0", "").trim();
            return raw;
        }
        if (raw.endsWith("/")) return raw.substring(0, raw.length() - 1);
        int slash = raw.indexOf('/');
        if (slash >= 0) {
            String idxStr = raw.substring(slash + 1);
            try {
                int idx = Integer.parseInt(idxStr);
                if (longNames != null && idx >= 0 && idx < longNames.length) {
                    int end = idx;
                    while (end < longNames.length && longNames[end] != '/' && longNames[end] != 0 && longNames[end] != '\n') end++;
                    return new String(longNames, idx, end - idx, StandardCharsets.UTF_8);
                }
            } catch (NumberFormatException ignored) {}
        }
        return raw;
    }
}
