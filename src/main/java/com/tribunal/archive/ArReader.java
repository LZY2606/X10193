package com.tribunal.archive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the System V / GNU {@code ar(1)} format used by {@code ar} /
 * {@code llvm-ar}. Supports the three name encodings that real toolchains
 * emit:
 * <ul>
 *   <li>short names terminated by '/'</li>
 *   <li>GNU long names via {@code //} member and {@code /offset} headers</li>
 *   <li>BSD style {@code #1/length} inline names</li>
 * </ul>
 * The reader is index based: no file system order is ever used by the
 * resolver to choose between members.
 */
public final class ArReader {
    public static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 60;

    public List<ArMember> read(byte[] data) throws ArFormatException {
        if (data == null || data.length < MAGIC.length) {
            throw new ArFormatException("archive shorter than ar magic");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new ArFormatException("bad ar magic");
            }
        }
        List<ArMember> members = new ArrayList<>();
        byte[] longNames = null;
        int pos = MAGIC.length;
        while (pos < data.length) {
            if (pos + HEADER_SIZE > data.length) {
                throw new ArFormatException("truncated member header at " + pos);
            }
            String rawName = ascii(data, pos, 16);
            String sizeField = ascii(data, pos + 48, 10).trim();
            long size;
            try {
                size = Long.parseLong(sizeField);
            } catch (NumberFormatException ex) {
                throw new ArFormatException("invalid member size '" + sizeField
                        + "' at offset " + pos);
            }
            long dataOffset = pos + HEADER_SIZE;
            if (size < 0 || dataOffset + size > data.length) {
                throw new ArFormatException("member at " + pos + " data runs past end of file");
            }
            byte[] memberData = new byte[(int) size];
            System.arraycopy(data, (int) dataOffset, memberData, 0, (int) size);
            String name;
            if (rawName.equals("//")) {
                longNames = memberData;
            } else if (rawName.equals("/") || rawName.startsWith("/SYM64")
                    || rawName.startsWith("/SYM")) {
                // Symbol index member: ignored (resolution uses its own scan).
            } else if (rawName.startsWith("#1/")) {
                int inlineLen = Integer.parseInt(rawName.substring(3).trim());
                if (inlineLen > size) {
                    throw new ArFormatException("BSD #1 name longer than member at " + pos);
                }
                name = trimNul(ascii(memberData, 0, inlineLen));
                byte[] withoutName = new byte[(int) (size - inlineLen)];
                System.arraycopy(memberData, inlineLen, withoutName, 0,
                        withoutName.length);
                memberData = withoutName;
                members.add(new ArMember(name, dataOffset, memberData));
            } else if (rawName.startsWith("/") && !rawName.trim().equals("/")) {
                int offset = Integer.parseInt(rawName.substring(1).trim());
                if (longNames == null || offset < 0 || offset >= longNames.length) {
                    throw new ArFormatException("GNU long name offset " + offset
                            + " out of longname table");
                }
                name = longName(longNames, offset);
                members.add(new ArMember(name, dataOffset, memberData));
            } else {
                int slash = rawName.indexOf('/');
                name = slash >= 0 ? rawName.substring(0, slash).trim()
                        : rawName.trim();
                members.add(new ArMember(name, dataOffset, memberData));
            }
            pos = (int) (dataOffset + size);
            if ((pos & 1) == 1) {
                pos++; // even alignment, byte is `\n`
                if (pos > data.length) {
                    // Padding missing on the very last member: tolerate it.
                    break;
                }
            }
        }
        return List.copyOf(members);
    }

    private static String ascii(byte[] data, int off, int len) {
        return new String(data, off, len, StandardCharsets.US_ASCII);
    }

    private static String trimNul(String s) {
        int nul = s.indexOf('\0');
        return nul >= 0 ? s.substring(0, nul) : s;
    }

    private static String longName(byte[] table, int offset) throws ArFormatException {
        int end = offset;
        while (end < table.length && table[end] != '/' && table[end] != '\n'
                && table[end] != 0) {
            end++;
        }
        if (end >= table.length) {
            throw new ArFormatException("unterminated long name at offset " + offset);
        }
        return new String(table, offset, end - offset, StandardCharsets.UTF_8);
    }
}
