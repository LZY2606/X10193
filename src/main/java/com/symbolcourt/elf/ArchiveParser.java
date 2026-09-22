package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the System V/GNU ar(5) archive format used by static libraries.
 * Handles the "/" symbol table, the "//" long-name table and BSD "#1/" names.
 */
public final class ArchiveParser {

    private static final byte[] GLOBAL_MAGIC = "!<arch>\n".getBytes();
    private static final int HEADER_SIZE = 60;

    public List<ArchiveMember> parse(byte[] data) {
        return parse(data, "archive");
    }

    public List<ArchiveMember> parse(byte[] data, String sourceName) {
        ByteReader r = new ByteReader(data, true);
        if (data.length < GLOBAL_MAGIC.length || !r.startsWith(0, GLOBAL_MAGIC)) {
            throw new ElfParseException(sourceName + ": bad ar magic");
        }
        long cursor = GLOBAL_MAGIC.length;
        byte[] longNames = null;
        List<ArchiveMember> members = new ArrayList<>();
        int order = 0;
        while (cursor < data.length) {
            if (cursor + HEADER_SIZE > data.length) {
                throw new ElfParseException(sourceName + ": truncated archive header at " + cursor);
            }
            String rawName = ascii(data, cursor, 16);
            long size;
            try {
                String sizeField = ascii(data, cursor + 48, 10).trim();
                size = Long.parseLong(sizeField);
            } catch (NumberFormatException e) {
                throw new ElfParseException(sourceName + ": bad member size at " + cursor, e);
            }
            String end = ascii(data, cursor + 58, 2);
            if (!end.equals("`\n")) {
                throw new ElfParseException(sourceName + ": bad archive header terminator");
            }
            long body = cursor + HEADER_SIZE;
            r.require(body, size);

            String name = rawName;
            boolean special = false;
            String trimmed = rawName.trim();
            if (trimmed.equals("/") || trimmed.equals("/SYM64/") || trimmed.equals("/SYM32/")) {
                special = true; // symbol table
            } else if (trimmed.equals("//")) {
                longNames = r.slice(body, (int) size);
                special = true;
            } else if (trimmed.endsWith("/")) {
                name = trimmed.substring(0, trimmed.length() - 1);
            } else if (trimmed.startsWith("/")) {
                if (longNames == null) {
                    throw new ElfParseException(sourceName + ": long name reference without // table");
                }
                int idx;
                try {
                    idx = Integer.parseInt(trimmed.substring(1));
                } catch (NumberFormatException e) {
                    throw new ElfParseException(sourceName + ": bad long name index", e);
                }
                name = readLongName(longNames, idx, sourceName);
            } else if (trimmed.startsWith("#1/")) {
                // BSD variant: name lives in the first bytes of the member body.
                int nameLen;
                try {
                    nameLen = Integer.parseInt(trimmed.substring(3));
                } catch (NumberFormatException e) {
                    throw new ElfParseException(sourceName + ": bad BSD name length", e);
                }
                if (nameLen > size) {
                    throw new ElfParseException(sourceName + ": BSD name longer than member");
                }
                name = new String(data, (int) body, nameLen, StandardCharsets.UTF_8).trim();
            }
            if (!special) {
                byte[] memberData;
                if (trimmed.startsWith("#1/")) {
                    int nameLen = Integer.parseInt(trimmed.substring(3));
                    memberData = r.slice(body + nameLen, (int) (size - nameLen));
                } else {
                    memberData = r.slice(body, (int) size);
                }
                members.add(new ArchiveMember(name, order++, memberData));
            }
            cursor = body + size;
            if ((size & 1) == 1) {
                cursor++; // padding newline
                if (cursor > data.length) {
                    // padding may be absent on the final member; tolerate exactly one byte over
                    cursor = data.length;
                }
            }
        }
        return members;
    }

    public boolean isArchive(byte[] data) {
        return data != null && data.length >= GLOBAL_MAGIC.length
                && new ByteReader(data, true).startsWith(0, GLOBAL_MAGIC);
    }

    private String readLongName(byte[] table, int offset, String src) {
        if (offset < 0 || offset >= table.length) {
            throw new ElfParseException(src + ": long name offset out of bounds");
        }
        int end = offset;
        while (end < table.length && table[end] != '/' && table[end] != '\n' && table[end] != 0) {
            end++;
        }
        return new String(table, offset, end - offset, StandardCharsets.UTF_8);
    }

    private String ascii(byte[] data, long off, int len) {
        if (off < 0 || off + len > data.length) {
            throw new ElfParseException("ar header read out of bounds");
        }
        return new String(data, (int) off, len, StandardCharsets.US_ASCII);
    }
}
