package com.example.tribunal.core.elf;

import com.example.tribunal.core.elf.ElfModels.ArchiveMember;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ArchiveParser {
    public static class ArchiveException extends IOException {
        public ArchiveException(String message) { super(message); }
    }

    private static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    public static boolean isArchive(byte[] bytes) {
        if (bytes == null || bytes.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) if (bytes[i] != MAGIC[i]) return false;
        return true;
    }

    public static List<ArchiveMember> parse(byte[] bytes) throws ArchiveException {
        if (!isArchive(bytes)) throw new ArchiveException("not a GNU/BSD archive");
        List<ArchiveMember> members = new ArrayList<>();
        int offset = MAGIC.length;
        String pendingLongName = null;
        while (offset < bytes.length) {
            if (offset + 60 > bytes.length) throw new ArchiveException("truncated archive header");
            String header = new String(bytes, offset, 60, StandardCharsets.US_ASCII);
            int size = parseDecimal(header.substring(48, 58), "member size");
            int dataOffset = offset + 60;
            if (dataOffset + size > bytes.length) throw new ArchiveException("archive member exceeds archive");
            String rawName = header.substring(0, 16);
            String name;
            if (rawName.startsWith("//")) {
                pendingLongName = new String(bytes, dataOffset, size, StandardCharsets.UTF_8);
            } else if (!rawName.startsWith("/") && !rawName.startsWith("#1/") && !rawName.startsWith("__.SYMDEF")) {
                int slash = rawName.indexOf('/');
                name = slash >= 0 ? rawName.substring(0, slash).trim() : rawName.trim();
                members.add(member(name, bytes, dataOffset, size));
            } else if (rawName.startsWith("/") && !rawName.startsWith("//") && pendingLongName != null) {
                int index = parseDecimal(rawName.substring(1).trim(), "long name index");
                int end = pendingLongName.indexOf('\n', index);
                if (end < 0) throw new ArchiveException("long name table is unterminated");
                name = pendingLongName.substring(index, end).replace("/", "").trim();
                members.add(member(name, bytes, dataOffset, size));
            } else if (rawName.startsWith("#1/")) {
                int nameLength = parseDecimal(rawName.substring(3).trim(), "BSD member name length");
                if (nameLength > size) throw new ArchiveException("BSD archive name overlaps member data");
                name = new String(bytes, dataOffset, nameLength, StandardCharsets.UTF_8).replace("\0", "").trim();
                members.add(member(name, bytes, dataOffset + nameLength, size - nameLength));
            }
            offset = dataOffset + size + (size & 1);
        }
        return members;
    }

    private static ArchiveMember member(String name, byte[] bytes, int offset, int size) {
        byte[] copy = new byte[size];
        System.arraycopy(bytes, offset, copy, 0, size);
        return new ArchiveMember(name, copy);
    }

    private static int parseDecimal(String text, String field) throws ArchiveException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ArchiveException("invalid " + field);
        }
    }
}
