package com.symcourt.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** System V / GNU ar archive. Read-only view over member bytes. */
public final class Archive {

    public static final class Member {
        public final String name;
        public final byte[] data;

        Member(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    public static boolean isArchive(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        byte[] magic = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 8; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    public static List<Member> parse(byte[] data) {
        if (!isArchive(data)) {
            throw new ElfParseException("not an ar archive");
        }
        List<Member> members = new ArrayList<>();
        byte[] longNames = new byte[0];
        int off = 8;
        while (off + 60 <= data.length) {
            String nameField = ascii(data, off, 16).trim();
            String sizeField = ascii(data, off + 48, 10).trim();
            if (data[off + 58] != 0x60 || data[off + 59] != '\n') {
                throw new ElfParseException("bad ar header terminator at offset " + off);
            }
            int size;
            try {
                size = Integer.parseInt(sizeField);
            } catch (NumberFormatException e) {
                throw new ElfParseException("bad ar member size at offset " + off);
            }
            if (size < 0 || off + 60 + size > data.length) {
                throw new ElfParseException("ar member extends past end of file at offset " + off);
            }
            byte[] body = new byte[size];
            System.arraycopy(data, off + 60, body, 0, size);
            if (nameField.equals("//")) {
                longNames = body;
            } else if (!nameField.equals("/") && !nameField.equals("/SYM64/")) {
                String name;
                if (nameField.startsWith("/") && nameField.length() > 1) {
                    int nameOff;
                    try {
                        nameOff = Integer.parseInt(nameField.substring(1));
                    } catch (NumberFormatException e) {
                        throw new ElfParseException("bad ar long-name reference: " + nameField);
                    }
                    if (nameOff < 0 || nameOff >= longNames.length) {
                        throw new ElfParseException("ar long-name offset out of range: " + nameOff);
                    }
                    int end = nameOff;
                    while (end < longNames.length && longNames[end] != '\n' && longNames[end] != 0) {
                        end++;
                    }
                    name = new String(longNames, nameOff, end - nameOff, StandardCharsets.UTF_8);
                    if (name.endsWith("/")) {
                        name = name.substring(0, name.length() - 1);
                    }
                } else {
                    name = nameField.endsWith("/")
                            ? nameField.substring(0, nameField.length() - 1) : nameField;
                }
                members.add(new Member(name, body));
            }
            off += 60 + size + (size & 1);
        }
        if (off != data.length && data.length - off > 1) {
            throw new ElfParseException("trailing garbage in archive");
        }
        return members;
    }

    private static String ascii(byte[] d, int off, int len) {
        return new String(d, off, len, StandardCharsets.US_ASCII);
    }

    private Archive() { }
}
