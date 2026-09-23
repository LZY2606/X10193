package com.example.symbolcourt.parser;

import com.example.symbolcourt.model.CourtModels.ArchiveIndexEntry;
import com.example.symbolcourt.model.CourtModels.ArchiveMember;
import com.example.symbolcourt.model.CourtModels.ParsedArchive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchiveReader {
    private static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
    private final byte[] data;

    private ArchiveReader(byte[] data) {
        this.data = data;
    }

    public static ParsedArchive read(byte[] data, String fileName, int loadOrder, String id) {
        return new ArchiveReader(data).readArchive(fileName, loadOrder, id);
    }

    private ParsedArchive readArchive(String fileName, int loadOrder, String id) {
        ParsedArchive archive = new ParsedArchive();
        archive.id = id;
        archive.fileName = fileName;
        archive.loadOrder = loadOrder;
        archive.index = List.of();
        archive.members = List.of();
        if (data.length < MAGIC.length || !startsWithMagic()) {
            archive.error = "not an ar archive";
            return archive;
        }

        List<ArchiveMember> members = new ArrayList<>();
        try {
            long cursor = MAGIC.length;
            while (cursor < data.length) {
                if (data.length - cursor < 60) {
                    archive.error = "archive member header truncated";
                    break;
                }
                String rawName = ascii(cursor, 16).trim();
                long size = parseLong(ascii(cursor + 48, 10).trim());
                long dataOffset = cursor + 60;
                if (size < 0 || dataOffset > data.length || size > data.length - dataOffset) {
                    archive.error = "archive member data truncated: " + rawName;
                    break;
                }
                ArchiveMember member = new ArchiveMember();
                member.headerOffset = cursor;
                member.dataOffset = dataOffset;
                member.size = size;
                member.name = resolveName(rawName, dataOffset, size);
                if (!rawName.equals("/") && !rawName.startsWith("/SYM") && !rawName.equals("//")) {
                    members.add(member);
                }
                cursor = dataOffset + size + (size & 1L);
            }
            archive.members = members;
            archive.index = readSymbolIndex();
        } catch (RuntimeException ex) {
            archive.error = ex.getMessage() == null ? "invalid archive" : ex.getMessage();
        }
        return archive;
    }

    private boolean startsWithMagic() {
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private List<ArchiveIndexEntry> readSymbolIndex() {
        List<ArchiveIndexEntry> entries = new ArrayList<>();
        if (data.length < MAGIC.length + 60) {
            return entries;
        }
        String first = ascii(MAGIC.length, 16).trim();
        if (!first.equals("/") && !first.equals("/SYM64/")) {
            return entries;
        }
        boolean sixtyFour = first.equals("/SYM64/");
        try {
            long size = parseLong(ascii(MAGIC.length + 48, 10).trim());
            long base = MAGIC.length + 60;
            if (base + size > data.length || size < 4) {
                return entries;
            }
            int count = (int) readNumber(base, 4, false);
            if (count < 0 || count > 1_000_000) {
                return entries;
            }
            int pointerSize = sixtyFour ? 8 : 4;
            long offsetsStart = base + 4;
            long namesStart = offsetsStart + (long) count * pointerSize;
            if (namesStart > base + size) {
                return entries;
            }
            Map<Long, String> memberByOffset = new HashMap<>();
            for (ArchiveMember member : membersAll()) {
                memberByOffset.put(member.dataOffset, member.name);
            }
            long nameCursor = namesStart;
            for (int i = 0; i < count; i++) {
                long memberOffset = readNumber(offsetsStart + (long) i * pointerSize, pointerSize, false);
                if (nameCursor >= base + size) {
                    break;
                }
                long end = nameCursor;
                while (end < base + size && data[(int) end] != 0) {
                    end++;
                }
                String symbol = new String(data, (int) nameCursor, (int) (end - nameCursor), StandardCharsets.UTF_8);
                String member = memberByOffset.get(memberOffset);
                if (member == null) {
                    member = "@" + memberOffset;
                }
                entries.add(new ArchiveIndexEntry(symbol, memberOffset, member));
                nameCursor = end + 1;
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }
        return entries;
    }

    private List<ArchiveMember> membersAll() {
        List<ArchiveMember> members = new ArrayList<>();
        long cursor = MAGIC.length;
        while (cursor + 60 <= data.length) {
            String rawName = ascii(cursor, 16).trim();
            long size = parseLong(ascii(cursor + 48, 10).trim());
            long dataOffset = cursor + 60;
            if (size < 0 || dataOffset > data.length || size > data.length - dataOffset) {
                break;
            }
            ArchiveMember member = new ArchiveMember();
            member.headerOffset = cursor;
            member.dataOffset = dataOffset;
            member.size = size;
            member.name = resolveName(rawName, dataOffset, size);
            if (!rawName.equals("/") && !rawName.startsWith("/SYM") && !rawName.equals("//")) {
                members.add(member);
            }
            cursor = dataOffset + size + (size & 1L);
        }
        return members;
    }

    private String resolveName(String rawName, long dataOffset, long size) {
        if (rawName.startsWith("/") && rawName.length() > 1 && Character.isDigit(rawName.charAt(1))) {
            LongStringTable table = findLongNames();
            if (table != null) {
                long offset = parseLong(rawName.substring(1));
                long limit = table.offset + table.size;
                long start = table.offset + offset;
                long end = start;
                while (end < limit && data[(int) end] != 0 && data[(int) end] != '\n' && data[(int) end] != '/') {
                    end++;
                }
                if (start >= table.offset && end <= limit && end > start) {
                    return new String(data, (int) start, (int) (end - start), StandardCharsets.UTF_8);
                }
            }
        }
        int slash = rawName.indexOf('/');
        return slash >= 0 ? rawName.substring(0, slash) : rawName;
    }

    private LongStringTable findLongNames() {
        long cursor = MAGIC.length;
        while (cursor + 60 <= data.length) {
            String name = ascii(cursor, 16).trim();
            long size = parseLong(ascii(cursor + 48, 10).trim());
            long dataOffset = cursor + 60;
            if (size < 0 || dataOffset > data.length || size > data.length - dataOffset) {
                return null;
            }
            if (name.equals("//")) {
                return new LongStringTable(dataOffset, size);
            }
            cursor = dataOffset + size + (size & 1L);
        }
        return null;
    }

    private record LongStringTable(long offset, long size) {
    }

    private long readNumber(long offset, int size, boolean littleEndian) {
        long value = 0;
        for (int i = 0; i < size; i++) {
            int b = data[(int) offset + i] & 0xff;
            value |= (long) b << (littleEndian ? 8 * i : 8 * (size - 1 - i));
        }
        return value;
    }

    private String ascii(long offset, int length) {
        return new String(data, (int) offset, length, StandardCharsets.US_ASCII);
    }

    private long parseLong(String value) {
        if (value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value);
    }
}
