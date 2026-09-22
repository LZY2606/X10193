package com.example.symbolcourt.elf;

import java.nio.charset.StandardCharsets;

/** Minimal SysV/GNU ar(5) archive reader. */
final class ArArchive {

    private static final String MAGIC = "!<arch>\n";

    private ArArchive() {
    }

    static ElfFile parse(String archiveName, byte[] data) {
        if (data.length < MAGIC.length()
                || !new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII).equals(MAGIC)) {
            throw new ElfParseException(archiveName + ": bad ar magic");
        }
        ElfFile arc = new ElfFile();
        arc.name = archiveName;
        arc.archive = true;
        long p = MAGIC.length();
        String longNameTable = "";
        int memberOrdinal = 0;
        while (p + 60 <= data.length) {
            String rawName = new String(data, (int) p, 16, StandardCharsets.US_ASCII).trim();
            String sizeField = new String(data, (int) p + 48, 10, StandardCharsets.US_ASCII).trim();
            long size;
            try {
                size = Long.parseLong(sizeField);
            } catch (NumberFormatException e) {
                throw new ElfParseException(archiveName + ": bad member size at " + p);
            }
            long body = p + 60;
            if (size < 0 || body + size > data.length) {
                throw new ElfParseException(archiveName + ": member '" + rawName + "' extends past end of archive");
            }
            byte[] memberData = new byte[(int) size];
            System.arraycopy(data, (int) body, memberData, 0, (int) size);
            if (rawName.equals("/")) {
                // symbol index member - not an object, skipped
            } else if (rawName.equals("//")) {
                longNameTable = new String(memberData, 0, memberData.length, StandardCharsets.US_ASCII);
            } else if (rawName.startsWith("/SYM64/") || rawName.equals("/SYM64")) {
                // 64-bit symbol index - ignored
            } else {
                String memberName = resolveName(rawName, longNameTable);
                if (memberName == null || memberName.isEmpty()) {
                    memberName = "member-" + memberOrdinal;
                }
                ElfFile member = ElfParser.parseMember(memberName, memberData);
                member.name = memberName;
                arc.members.add(member);
                memberOrdinal++;
            }
            p = body + size;
            if ((size & 1) == 1) {
                p++;
            }
        }
        return arc;
    }

    private static String resolveName(String rawName, String longNameTable) {
        if (rawName.startsWith("/") && rawName.length() > 1) {
            try {
                int off = Integer.parseInt(rawName.substring(1));
                if (off >= 0 && off < longNameTable.length()) {
                    int end = off;
                    while (end < longNameTable.length()
                            && longNameTable.charAt(end) != '/'
                            && longNameTable.charAt(end) != '\n') {
                        end++;
                    }
                    return longNameTable.substring(off, end);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
            return "";
        }
        int slash = rawName.indexOf('/');
        return slash >= 0 ? rawName.substring(0, slash) : rawName;
    }
}
