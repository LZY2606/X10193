package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader for Unix ar archives used by static libraries. Supports the GNU "/" symbol
 * table, the "//" long-name table and BSD #1/ long names. Malformed members are reported as
 * isolated member errors rather than failing sibling members.
 */
public class ArArchive {
    public String origin;
    public List<Member> members = new ArrayList<>();
    /** symbol name -> member index (GNU __.SYMDEF style index). */
    public Map<String, List<Integer>> symbolIndex = new LinkedHashMap<>();
    public String error;

    public static class Member {
        public String name;
        public byte[] data;
        public int index;
        public String error;
        public ElfImage image;
    }

    private static final byte[] MAGIC = "!<arch>\n".getBytes();

    public static ArArchive read(byte[] data, String origin) {
        ArArchive archive = new ArArchive();
        archive.origin = origin;
        if (data == null || data.length < MAGIC.length) {
            throw new ElfParseException("archive too short");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new ElfParseException("bad ar magic");
            }
        }
        byte[] longNames = null;
        int cursor = MAGIC.length;
        int index = 0;
        while (cursor < data.length) {
            if (cursor + 60 > data.length) {
                throw new ElfParseException("truncated ar member header at " + cursor);
            }
            String rawName = ascii(data, cursor, 16);
            String sizeText = ascii(data, cursor + 48, 10).trim();
            long size;
            try {
                size = Long.parseLong(sizeText);
            } catch (NumberFormatException e) {
                throw new ElfParseException("bad ar member size: '" + sizeText + "'");
            }
            long dataStart = cursor + 60L;
            if (size < 0 || dataStart + size > data.length) {
                throw new ElfParseException("ar member data out of bounds for " + rawName.trim());
            }
            byte[] memberData = new byte[(int) size];
            System.arraycopy(data, (int) dataStart, memberData, 0, (int) size);
            Member member = new Member();
            member.index = index++;
            member.name = resolveName(rawName, memberData, archive);
            String trimmed = rawName.trim();
            if (trimmed.equals("/") || trimmed.equals("/SYM/") || trimmed.equals("__.SYMDEF")) {
                parseSymbolTable(memberData, archive);
            } else if (trimmed.equals("//") || trimmed.equals("/SYM64/")) {
                if (trimmed.equals("//")) {
                    longNames = memberData;
                } else {
                    parseSymbolTable(memberData, archive);
                }
            } else {
                if (member.name.startsWith("/") && longNames != null) {
                    try {
                        int offset = Integer.parseInt(member.name.substring(1).trim());
                        member.name = gnuLongName(longNames, offset);
                    } catch (NumberFormatException ignored) {
                        // keep the raw name
                    }
                }
                archive.members.add(member);
            }
            cursor = (int) (dataStart + size);
            if ((size & 1) == 1) {
                cursor++;
            }
        }
        return archive;
    }

    private static String resolveName(String rawName, byte[] memberData, ArArchive archive) {
        String name = rawName;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            name = name.substring(0, slash);
        }
        if (name.startsWith("#1/")) {
            int length = Integer.parseInt(name.substring(3).trim());
            if (length <= memberData.length) {
                int end = 0;
                while (end < length && memberData[end] != 0) {
                    end++;
                }
                return ascii(memberData, 0, end);
            }
        }
        return name;
    }

    private static String gnuLongName(byte[] longNames, int offset) {
        if (offset < 0 || offset >= longNames.length) {
            throw new ElfParseException("long name offset out of range");
        }
        int end = offset;
        while (end < longNames.length && longNames[end] != '/' && longNames[end] != '\n') {
            end++;
        }
        return new String(longNames, offset, end - offset, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void parseSymbolTable(byte[] data, ArArchive archive) {
        if (data.length < 4) {
            throw new ElfParseException("archive symbol table too short");
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        int count = buffer.getInt();
        if (count < 0 || 4L + count * 4L > data.length) {
            throw new ElfParseException("archive symbol table count out of bounds");
        }
        int[] memberOffsets = new int[count];
        for (int i = 0; i < count; i++) {
            memberOffsets[i] = buffer.getInt();
        }
        for (int i = 0; i < count; i++) {
            int nameStart = buffer.position();
            int nameEnd = nameStart;
            while (nameEnd < data.length && data[nameEnd] != 0) {
                nameEnd++;
            }
            if (nameEnd >= data.length) {
                throw new ElfParseException("archive symbol name unterminated");
            }
            String symbol = new String(data, nameStart, nameEnd - nameStart,
                    java.nio.charset.StandardCharsets.US_ASCII);
            buffer.position(nameEnd + 1);
            int memberOffset = memberOffsets[i];
            archive.symbolIndex.computeIfAbsent(symbol, k -> new ArrayList<>())
                    .add(memberOffset);
        }
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
