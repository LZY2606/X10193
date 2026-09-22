package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the ar(1) container used for static libraries. Handles GNU long
 * names (// member), BSD long names (#1/len), the symbol index (/ and /SYM64)
 * and the thin-archive rejection. Members are retained lazily; the adjudicator
 * decides which members are actually extracted.
 */
public final class ArchiveParser {

    private static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    private final byte[] data;
    private final ByteReader r;

    public ArchiveParser(byte[] data) {
        this.data = data;
        this.r = new ByteReader(data, true); // ar is ASCII; numeric fields are text
    }

    public Archive parse(String sourceName) throws ElfException {
        if (data == null || data.length < MAGIC.length) {
            throw new ElfException("not an archive (too short)");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new ElfException("bad ar magic");
            }
        }
        Archive ar = new Archive(sourceName);
        long pos = MAGIC.length;
        byte[] longNames = null;
        int guard = 0;
        while (pos < data.length) {
            if (guard++ > 1_000_000) {
                throw new ElfException("archive member count limit exceeded");
            }
            if (pos + 60 > data.length) {
                throw new ElfException("truncated archive header");
            }
            String rawName = ascii(pos, 16);
            long size = parseDecimal(pos + 48, 10, "member size");
            String name = rawName;
            int bsdNameLen = 0;
            if (rawName.equals("/") || rawName.startsWith("/SYM64") || rawName.equals("__.SYMDEF")) {
                name = "/"; // symbol index / ranlib table
            } else if (rawName.trim().equals("//")) {
                name = "//";
            } else if (rawName.startsWith("/")) {
                // GNU long name offset
                long off = parseDecimal(pos + 1, 15, "long name offset");
                if (longNames == null) {
                    throw new ElfException("long-name member before // table");
                }
                name = readLongName(longNames, off);
            } else if (rawName.startsWith("#1/")) {
                bsdNameLen = (int) parseDecimal(pos + 3, 13, "bsd name len");
            } else {
                int slash = rawName.indexOf('/');
                name = slash >= 0 ? rawName.substring(0, slash) : rawName.trim();
            }
            long dataStart = pos + 60;
            r.require(dataStart, size);
            if (name.equals("//")) {
                longNames = r.bytes(dataStart, (int) size);
            } else if (!name.equals("/")) {
                long contentStart = dataStart;
                long contentSize = size;
                if (bsdNameLen > 0) {
                    if (bsdNameLen > size) {
                        throw new ElfException("BSD name longer than member");
                    }
                    name = ascii(dataStart, bsdNameLen).replace("\0", "").trim();
                    contentStart += bsdNameLen;
                    contentSize -= bsdNameLen;
                }
                byte[] member = r.bytes(contentStart, (int) contentSize);
                ar.members.put(name, member);
            }
            pos = dataStart + size;
            if ((pos & 1) == 1 && pos < data.length && data[(int) pos] == '\n') {
                pos++; // padding
            }
        }
        return ar;
    }

    private String ascii(long off, int len) throws ElfException {
        return new String(r.bytes(off, len), StandardCharsets.US_ASCII);
    }

    private long parseDecimal(long off, int len, String what) throws ElfException {
        String s = ascii(off, len).trim();
        if (s.isEmpty()) {
            throw new ElfException("missing " + what);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ElfException("bad " + what + ": '" + s + "'");
        }
    }

    private String readLongName(byte[] table, long offset) throws ElfException {
        if (offset < 0 || offset >= table.length) {
            throw new ElfException("long name offset out of range");
        }
        int end = (int) offset;
        while (end < table.length && table[end] != '/' && table[end] != '\n' && table[end] != 0) {
            end++;
        }
        return new String(table, (int) offset, end - (int) offset, StandardCharsets.UTF_8);
    }

    public static boolean looksLikeArchive(byte[] data) {
        return data != null && data.length >= MAGIC.length
                && data[0] == '!' && data[1] == '<' && data[6] == '>';
    }

    /** Convenience: extract the list of member names in order. */
    public static List<String> memberNames(Archive ar) {
        return new ArrayList<>(ar.members.keySet());
    }
}
