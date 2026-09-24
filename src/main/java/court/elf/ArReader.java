package court.elf;

import java.nio.charset.StandardCharsets;

/** Minimal ar(5) reader supporting BSD and GNU long-name tables; bounds checked. */
public final class ArReader {

    private static final String MAGIC = "!<arch>\n";

    public ArArchive parse(byte[] data) throws ElfException {
        if (data == null || data.length < MAGIC.length()) {
            throw new ElfException("archive too small");
        }
        String head = new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII);
        if (!MAGIC.equals(head)) {
            throw new ElfException("bad ar magic");
        }
        ArArchive ar = new ArArchive();
        ar.raw = data;
        byte[] longNames = null;
        int p = MAGIC.length();
        int guard = 0;
        while (p < data.length) {
            if (guard++ > 1_000_000) throw new ElfException("archive member loop runaway");
            if (data.length - p < 60) {
                throw new ElfException("truncated ar member header at " + p);
            }
            String rawName = new String(data, p, 16, StandardCharsets.US_ASCII).trim();
            long size = parseDecimal(data, p + 48, 10, "ar size");
            long mtime = parseDecimalOrZero(data, p + 16, 12);
            int uid = (int) parseDecimalOrZero(data, p + 28, 6);
            int gid = (int) parseDecimalOrZero(data, p + 34, 6);
            int mode = (int) parseDecimalOrZero(data, p + 40, 8);
            String end = new String(data, p + 58, 2, StandardCharsets.US_ASCII);
            if (!"`\n".equals(end)) {
                throw new ElfException("bad ar header terminator at " + p);
            }
            int body = p + 60;
            if (size < 0 || body + size > data.length) {
                throw new ElfException("ar member body out of bounds");
            }
            int len = (int) size;
            byte[] bodyData = new byte[len];
            System.arraycopy(data, body, bodyData, 0, len);

            ArMember m = new ArMember();
            m.data = bodyData;
            m.mtime = mtime;
            m.uid = uid;
            m.gid = gid;
            m.mode = mode;

            if (rawName.startsWith("//")) {
                longNames = bodyData;
            } else if (rawName.equals("/") || rawName.startsWith("/SYM")) {
                // symbol index / 64-bit symbol index: skip
            } else if (rawName.startsWith("#1/")) {
                int nlen = (int) parseDecimal(rawName.substring(2), "bsd long name length");
                if (nlen > len) throw new ElfException("BSD long name exceeds member");
                m.name = new String(bodyData, 0, indexOfZero(bodyData, nlen),
                        StandardCharsets.UTF_8);
                m.longName = true;
                byte[] trimmed = new byte[len - nlen];
                System.arraycopy(bodyData, nlen, trimmed, 0, trimmed.length);
                m.data = trimmed;
                ar.members.add(m);
            } else if (rawName.startsWith("/")) {
                int off = (int) parseDecimal(rawName.substring(1), "gnu name offset");
                m.name = gnuName(longNames, off);
                m.longName = true;
                ar.members.add(m);
            } else {
                int slash = rawName.indexOf('/');
                m.name = slash >= 0 ? rawName.substring(0, slash) : rawName;
                ar.members.add(m);
            }
            p = body + len;
            if ((len & 1) == 1) p++; // 2-byte alignment padding
        }
        return ar;
    }

    private static int indexOfZero(byte[] b, int max) {
        for (int i = 0; i < max && i < b.length; i++) {
            if (b[i] == 0) return i;
        }
        return max;
    }

    private static String gnuName(byte[] longNames, int off) throws ElfException {
        if (longNames == null || off < 0 || off >= longNames.length) {
            throw new ElfException("GNU long name offset out of range: " + off);
        }
        int end = off;
        while (end < longNames.length && longNames[end] != '/' && longNames[end] != '\n'
                && longNames[end] != 0) {
            end++;
        }
        return new String(longNames, off, end - off, StandardCharsets.UTF_8);
    }

    private static long parseDecimal(byte[] d, int off, int n, String what) throws ElfException {
        try {
            String s = new String(d, off, n, StandardCharsets.US_ASCII).trim();
            return parseDecimal(s, what);
        } catch (ElfException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ElfException("invalid " + what);
        }
    }

    private static long parseDecimalOrZero(byte[] d, int off, int n) {
        try {
            String s = new String(d, off, n, StandardCharsets.US_ASCII).trim();
            return s.isEmpty() ? 0 : Long.parseLong(s);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static long parseDecimal(String s, String what) throws ElfException {
        if (s == null || s.isEmpty()) throw new ElfException("empty " + what);
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw new ElfException("bad " + what + ": " + s);
            v = v * 10 + (c - '0');
        }
        return v;
    }
}
