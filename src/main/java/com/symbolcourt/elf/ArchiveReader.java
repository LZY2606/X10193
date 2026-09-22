package com.symbolcourt.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ArchiveReader {

    private static final String MAGIC = "!<arch>\n";

    private final byte[] data;

    public ArchiveReader(byte[] data) {
        this.data = data;
        if (data == null || data.length < MAGIC.length()) {
            throw new ElfParseException("file too short for ar archive");
        }
        String head = new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII);
        if (!MAGIC.equals(head)) {
            throw new ElfParseException("bad ar magic");
        }
    }

    public List<ArchiveMember> members() {
        List<ArchiveMember> members = new ArrayList<>();
        int pos = MAGIC.length();
        int guard = 0;
        while (pos < data.length) {
            if (guard++ > 1_000_000) {
                throw new ElfParseException("archive member chain too long");
            }
            if (pos + 60 > data.length) {
                throw new ElfParseException("truncated archive member header at " + pos);
            }
            String rawName = ascii(pos, 16);
            long size = parseLong(pos + 48, 10);
            long bodyOffset = pos + 60L;
            if (size < 0 || bodyOffset > data.length || bodyOffset + size > data.length) {
                throw new ElfParseException("archive member body out of bounds at " + pos);
            }
            String name = rawName.trim();
            byte[] body = new byte[(int) size];
            System.arraycopy(data, (int) bodyOffset, body, 0, (int) size);
            String terminator = ascii(pos + 58, 2);
            if (!terminator.equals("`\n")) {
                throw new ElfParseException("bad archive header terminator at " + pos);
            }
            long next = bodyOffset + size;
            if (size % 2 == 1 && next < data.length && data[(int) next] == '\n') {
                next++;
            }
            if (!"/".equals(name) && !name.startsWith("/SYM64") && !"__.SYMDEF".equals(name)) {
                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1);
                }
                members.add(new ArchiveMember(name, body, bodyOffset));
            }
            if (next <= pos || next > data.length) {
                throw new ElfParseException("archive member chain not advancing at " + pos);
            }
            pos = (int) next;
        }
        return members;
    }

    private String ascii(int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private long parseLong(int offset, int length) {
        String token = ascii(offset, length).trim();
        if (token.isEmpty()) {
            throw new ElfParseException("missing numeric field at " + offset);
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            throw new ElfParseException("invalid numeric field '" + token + "'");
        }
    }
}
