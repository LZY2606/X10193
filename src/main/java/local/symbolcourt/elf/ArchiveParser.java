package local.symbolcourt.elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArchiveParser {
    private static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    private final byte[] data;

    public ArchiveParser(byte[] data) {
        this.data = data;
    }

    public static Archive parse(byte[] data, String fileName) {
        return new ArchiveParser(data).parse(fileName);
    }

    public Archive parse(String fileName) {
        if (data.length < MAGIC.length) {
            throw new ElfParseException("archive is too short");
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (data[index] != MAGIC[index]) {
                throw new ElfParseException("missing archive magic");
            }
        }
        List<RawMember> rawMembers = new ArrayList<>();
        List<SymbolTable> symbolTables = new ArrayList<>();
        byte[] longNames = null;
        int offset = MAGIC.length;
        while (offset < data.length) {
            if (offset + 60 > data.length) {
                throw new ElfParseException("truncated archive member header");
            }
            String rawName = ascii(offset, 16);
            String sizeText = ascii(offset + 48, 10).trim();
            if (!sizeText.matches("\\d+")) {
                throw new ElfParseException("invalid archive member size");
            }
            int size = Integer.parseInt(sizeText);
            int start = offset + 60;
            if (start > data.length || start + size > data.length) {
                throw new ElfParseException("archive member exceeds file");
            }
            byte[] content = new byte[size];
            System.arraycopy(data, start, content, 0, size);
            String marker = rawName.trim();
            if (marker.equals("//")) {
                longNames = content;
            } else if (!marker.equals("/") && !marker.startsWith("/SYM64/")) {
                rawMembers.add(new RawMember(marker, content, start));
            } else {
                symbolTables.add(new SymbolTable(marker.startsWith("/SYM64/") ? 8 : 4, content));
            }
            offset = start + size;
            if ((offset & 1) == 1) {
                if (offset < data.length && data[offset] == '\n') {
                    offset++;
                } else {
                    offset++;
                }
            }
        }
        Map<String, ArchiveMember> members = new LinkedHashMap<>();
        for (RawMember rawMember : rawMembers) {
            String name = resolveName(rawMember.name(), longNames);
            members.put(name, new ArchiveMember(name, rawMember.content(), rawMember.dataOffset()));
        }
        Map<String, Integer> symbolMap = new LinkedHashMap<>();
        for (SymbolTable symbolTable : symbolTables) {
            parseSymbolTable(symbolTable, rawMembers, symbolMap);
        }
        return new Archive(fileName, List.copyOf(members.values()), Map.copyOf(symbolMap));
    }

    private String resolveName(String marker, byte[] longNames) {
        if (marker.startsWith("/")) {
            String suffix = marker.substring(1).trim();
            if (suffix.matches("\\d+") && longNames != null) {
                int start = Integer.parseInt(suffix);
                if (start < 0 || start >= longNames.length) {
                    throw new ElfParseException("long name offset outside archive name table");
                }
                int end = start;
                while (end < longNames.length && longNames[end] != '/' && longNames[end] != '\n'
                        && longNames[end] != 0) {
                    end++;
                }
                return new String(longNames, start, end - start, StandardCharsets.UTF_8);
            }
            return suffix.isBlank() ? "(anonymous)" : suffix;
        }
        if (marker.startsWith("#1/")) {
            return marker.substring(3);
        }
        int slash = marker.indexOf('/');
        return slash < 0 ? marker : marker.substring(0, slash);
    }

    private String ascii(int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static String strings(byte[] content, int start) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int index = start;
        while (index < content.length && content[index] != 0) {
            output.write(content[index++]);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private void parseSymbolTable(SymbolTable table, List<RawMember> members, Map<String, Integer> target) {
        byte[] content = table.content();
        if (content.length < 4) {
            throw new ElfParseException("truncated archive symbol table");
        }
        int count = readBeInt(content, 0);
        int pointerSize = table.pointerSize();
        int namesStart = 4 + count * pointerSize;
        if (count < 0 || namesStart > content.length) {
            throw new ElfParseException("archive symbol table exceeds member");
        }
        List<Long> offsets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int position = 4 + index * pointerSize;
            offsets.add(pointerSize == 8 ? readBeLong(content, position) : Integer.toUnsignedLong(readBeInt(content, position)));
        }
        int namePosition = namesStart;
        for (int index = 0; index < count; index++) {
            if (namePosition >= content.length) {
                throw new ElfParseException("truncated archive symbol name");
            }
            int end = namePosition;
            while (end < content.length && content[end] != 0) {
                end++;
            }
            String symbolName = new String(content, namePosition, end - namePosition, StandardCharsets.UTF_8);
            long memberDataOffset = offsets.get(index);
            int memberIndex = -1;
            for (int candidate = 0; candidate < members.size(); candidate++) {
                if (members.get(candidate).dataOffset() - 60L == memberDataOffset) {
                    memberIndex = candidate;
                    break;
                }
            }
            if (memberIndex >= 0) {
                target.putIfAbsent(symbolName, memberIndex);
            }
            namePosition = end + 1;
        }
    }

    private static int readBeInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    }

    private static long readBeLong(byte[] bytes, int offset) {
        long value = 0;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (bytes[offset + index] & 0xff);
        }
        return value;
    }

    private record RawMember(String name, byte[] content, int dataOffset) {
    }

    private record SymbolTable(int pointerSize, byte[] content) {
    }

    public record Archive(String fileName, List<ArchiveMember> members, Map<String, Integer> symbolMap) {
    }

    public record ArchiveMember(String name, byte[] content, int dataOffset) {
    }
}
