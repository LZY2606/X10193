package com.court.elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader for the Unix ar(5) container used by static archives (lib*.a).
 * Understands the SysV/GNU symbol table ("/"), the GNU long-name table ("/"
 * or "//"), and BSD-style "#1/N" extended names.
 *
 * The archive symbol table is used for lazy member extraction: names listed
 * there are the only symbols a member can satisfy "for free" before the
 * member itself is parsed.
 */
public class ArchiveParser {

    public static final String MAGIC = "!<arch>\n";

    public static class ArchiveMember {
        private final String name;
        private final byte[] data;
        private final List<String> symbolTableNames;
        private final boolean elf;

        ArchiveMember(String name, byte[] data, List<String> symbolTableNames, boolean elf) {
            this.name = name;
            this.data = data;
            this.symbolTableNames = symbolTableNames;
            this.elf = elf;
        }

        public String getName() { return name; }
        public byte[] getData() { return data; }
        public List<String> getSymbolTableNames() { return symbolTableNames; }
        public boolean isElf() { return elf; }
    }

    public static boolean isArchive(byte[] data) {
        if (data == null || data.length < MAGIC.length()) return false;
        for (int i = 0; i < MAGIC.length(); i++) {
            if (data[i] != MAGIC.charAt(i)) return false;
        }
        return true;
    }

    /** @return members keyed by their archive names, in on-disk order */
    public static Map<String, ArchiveMember> parse(byte[] data) throws ElfParseException {
        if (!isArchive(data)) throw new ElfParseException("not an ar archive");
        Map<String, ArchiveMember> out = new LinkedHashMap<>();
        long pos = MAGIC.length();
        byte[] longNames = new byte[0];
        // First pass to find the long-name table before member names are resolved.
        ByteArrayOutputStream longNameBuf = new ByteArrayOutputStream();
        long scan = MAGIC.length();
        while (scan + 60 <= data.length) {
            String header = new String(data, (int) scan, 60, StandardCharsets.US_ASCII);
            if (!header.endsWith("`\n")) {
                throw new ElfParseException("bad ar member header at " + scan);
            }
            long size = parseLong(header.substring(48, 58).trim());
            long dataStart = scan + 60;
            if (dataStart + size > data.length) {
                throw new ElfParseException("ar member extends beyond archive: " + header.substring(0, 16));
            }
            String rawName = header.substring(0, 16);
            if (rawName.trim().equals("//")) {
                longNameBuf.write(data, (int) dataStart, (int) size);
            }
            scan = dataStart + size;
            if ((size & 1) == 1) scan++;
        }
        longNames = longNameBuf.toByteArray();

        pos = MAGIC.length();
        int memberIndex = 0;
        while (pos + 60 <= data.length) {
            String header = new String(data, (int) pos, 60, StandardCharsets.US_ASCII);
            if (!header.endsWith("`\n")) {
                throw new ElfParseException("bad ar member header at " + pos);
            }
            long size = parseLong(header.substring(48, 58).trim());
            long dataStart = pos + 60;
            String rawName = header.substring(0, 16);
            String trimmedRaw = rawName.trim();
            if (trimmedRaw.equals("//")) {
                pos = dataStart + size;
                if ((size & 1) == 1) pos++;
                memberIndex++;
                continue;
            }
            String name = resolveName(rawName, longNames, dataStart, data);
            byte[] body = new byte[(int) size];
            System.arraycopy(data, (int) dataStart, body, 0, (int) size);

            if (name.equals("/") || name.equals("/SYM64/") || name.equals("//")) {
                // archive symbol table or GNU long-name table, skip
            } else if (name.equals("__.SYMDEF") || name.startsWith("__.SYMDEF ")) {
                // ranlib table, skip
            } else {
                List<String> exported = archiveSymbolNames(body, false);
                boolean elf = body.length >= 4 && body[0] == 0x7f && body[1] == 'E';
                out.put(name, new ArchiveMember(name, body, exported, elf));
            }
            pos = dataStart + size;
            if ((size & 1) == 1) pos++;
            memberIndex++;
        }
        if (pos != data.length && !(pos == data.length - 1)) {
            // tolerate trailing newline
        }
        return out;
    }

    private static List<String> archiveSymbolNames(byte[] memberData, boolean unused) {
        // The member-local list is populated lazily by the resolution engine after
        // ELF parsing; the archive-wide symbol table is handled in symbolTableOf().
        return new ArrayList<>();
    }

    /**
     * Parse the SysV/GNU archive symbol table (first member named "/" or "/SYM64/").
     * Returns a map of exported symbol name -> member ordinal (0-based over
     * real members, excluding the symbol/name tables).
     */
    public static Map<String, Integer> symbolTable(byte[] data) throws ElfParseException {
        Map<String, Integer> result = new LinkedHashMap<>();
        long pos = MAGIC.length();
        int memberOrdinal = 0;
        // We must know each member's size/name in order; collect member offsets first.
        List<long[]> memberRanges = new ArrayList<>(); // {dataStart, size}
        List<String> rawNames = new ArrayList<>();
        while (pos + 60 <= data.length) {
            String header = new String(data, (int) pos, 60, StandardCharsets.US_ASCII);
            long size = parseLong(header.substring(48, 58).trim());
            memberRanges.add(new long[]{pos + 60, size});
            rawNames.add(header.substring(0, 16));
            pos = pos + 60 + size;
            if ((size & 1) == 1) pos++;
        }
        for (int i = 0; i < memberRanges.size(); i++) {
            String raw = rawNames.get(i).trim();
            if (!(raw.equals("/") || raw.equals("/SYM64/"))) {
                memberOrdinal++;
                continue;
            }
            long ds = memberRanges.get(i)[0];
            long sz = memberRanges.get(i)[1];
            if (sz < 4) return result;
            boolean is64 = raw.equals("/SYM64/");
            int count = readBe32(data, ds);
            int ptrSize = is64 ? 8 : 4;
            long expected = 4L + (long) count * ptrSize;
            if (sz < expected) throw new ElfParseException("archive symbol table truncated");
            long strStart = ds + expected;
            int ord = 0;
            int realOrd = 0;
            for (int s = 0; s < count; s++) {
                long memberPtr;
                if (is64) memberPtr = readBe64(data, ds + 4 + s * 8L);
                else memberPtr = readBe32(data, ds + 4 + s * 4L);
                long strOff = strStart;
                // find name end
                int end = (int) strOff;
                while (end < data.length && data[end] != 0) end++;
                String nm = new String(data, (int) strOff, end - (int) strOff,
                        StandardCharsets.US_ASCII);
                strStart = end + 1;
                int memberIndex = ordinalForOffset(data, memberRanges, rawNames, memberPtr);
                result.put(nm, memberIndex);
                ord++;
            }
            return result;
        }
        return result;
    }

    private static int ordinalForOffset(byte[] data, List<long[]> ranges, List<String> rawNames,
                                        long headerOffset) {
        int realIndex = 0;
        for (int i = 0; i < ranges.size(); i++) {
            String raw = rawNames.get(i).trim();
            if (raw.equals("/") || raw.equals("/SYM64/") || raw.equals("//")) {
                if (headerOffset == ranges.get(i)[0] - 60) return realIndex;
                continue;
            }
            if (headerOffset == ranges.get(i)[0] - 60) return realIndex;
            realIndex++;
        }
        return -1;
    }

    private static int readBe32(byte[] data, long off) {
        long v = ((data[(int) off] & 0xffL) << 24)
                | ((data[(int) off + 1] & 0xffL) << 16)
                | ((data[(int) off + 2] & 0xffL) << 8)
                | (data[(int) off + 3] & 0xffL);
        return (int) v;
    }

    private static long readBe64(byte[] data, long off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (data[(int) off + i] & 0xffL);
        return v;
    }

    private static String resolveName(String rawName, byte[] longNames, long dataStart, byte[] data)
            throws ElfParseException {
        String n = rawName;
        String trimmed = n.trim();
        if (trimmed.equals("/") || trimmed.equals("/SYM64/") || trimmed.equals("//")) {
            return trimmed;
        }
        if (n.startsWith("/") && n.trim().length() > 1 && n.trim().substring(1).chars()
                .allMatch(Character::isDigit)) {
            int idx = Integer.parseInt(n.trim().substring(1));
            if (idx < 0 || idx >= longNames.length) {
                throw new ElfParseException("long name index out of range: " + idx);
            }
            int end = idx;
            while (end < longNames.length && longNames[end] != '/' && longNames[end] != '\n') end++;
            return new String(longNames, idx, end - idx, StandardCharsets.US_ASCII);
        }
        if (n.trim().equals("//")) return "//";
        if (n.startsWith("#1/")) {
            int len = Integer.parseInt(n.trim().substring(3));
            if (len > dataStart + 0 && (long) dataStart + len <= data.length) {
                int end = (int) dataStart;
                while (end < dataStart + len && data[end] != 0) end++;
                return new String(data, (int) dataStart, end - (int) dataStart,
                        StandardCharsets.US_ASCII);
            }
        }
        String bare = n.trim();
        int slash = bare.indexOf('/');
        if (slash >= 0) return bare.substring(0, slash);
        return bare;
    }

    private static long parseLong(String s) throws ElfParseException {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ElfParseException("bad ar size field: '" + s + "'");
        }
    }
}
