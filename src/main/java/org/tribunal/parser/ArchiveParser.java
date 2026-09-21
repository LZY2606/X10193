package org.tribunal.parser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.ElfFile;

public class ArchiveParser {
    private static final byte[] GLOBAL_MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BSD_GLOBAL_MAGIC = "!<thin>\n".getBytes(StandardCharsets.US_ASCII);

    public boolean supports(byte[] data) {
        return startsWith(data, GLOBAL_MAGIC) || startsWith(data, BSD_GLOBAL_MAGIC);
    }

    public List<ArchiveMember> parse(String archiveId, String archiveName, byte[] data) {
        if (!supports(data)) {
            return List.of();
        }
        List<RawMember> rawMembers = readMembers(data);
        Map<String, String> gnuNames = parseGnuLongNames(rawMembers);
        List<ArchiveMember> result = new ArrayList<>();
        int ordinal = 0;
        for (RawMember raw : rawMembers) {
            String rawName = raw.headerName().trim();
            if (rawName.equals("/") || rawName.equals("/SYM64/") || rawName.equals("//")) {
                continue;
            }
            String memberName = resolveName(rawName, gnuNames);
            String unitId = archiveId + "#" + ordinal;
            try {
                ElfFile elf = new ElfParser(unitId, archiveName + "(" + memberName + ")", memberName)
                        .parse(raw.payload());
                result.add(new ArchiveMember(memberName, true, null, elf));
            } catch (Exception ex) {
                result.add(new ArchiveMember(memberName, false, ex.getMessage(), null));
            }
            ordinal++;
        }
        return result;
    }

    private List<RawMember> readMembers(byte[] data) {
        int offset = 8;
        List<RawMember> rawMembers = new ArrayList<>();
        while (offset < data.length) {
            if (offset + 60 > data.length) {
                break;
            }
            String header = new String(data, offset, 60, StandardCharsets.US_ASCII);
            if (!header.endsWith("`\n")) {
                break;
            }
            String name = header.substring(0, 16);
            long size;
            try {
                size = Long.parseLong(header.substring(48, 58).trim());
            } catch (NumberFormatException ex) {
                break;
            }
            if (size < 0 || offset + 60L + size > data.length) {
                break;
            }
            byte[] payload = new byte[(int) size];
            System.arraycopy(data, offset + 60, payload, 0, (int) size);
            rawMembers.add(new RawMember(name.trim(), offset + 60, payload));
            offset += 60 + (int) size;
            if ((size & 1) == 1) {
                offset++;
            }
        }
        return rawMembers;
    }

    private Map<String, String> parseGnuLongNames(List<RawMember> rawMembers) {
        Map<String, String> result = new HashMap<>();
        for (RawMember member : rawMembers) {
            if (!member.headerName().trim().equals("//")) {
                continue;
            }
            int cursor = 0;
            int start = 0;
            for (int i = 0; i < member.payload().length; i++) {
                if (member.payload()[i] == '/' && i + 1 < member.payload().length && member.payload()[i + 1] == '\n') {
                    result.put(Integer.toString(start),
                            new String(member.payload(), start, i - start, StandardCharsets.UTF_8));
                    i++;
                    cursor = i + 1;
                    start = cursor;
                } else if (member.payload()[i] == '\n') {
                    cursor = i + 1;
                    start = cursor;
                } else {
                    cursor++;
                }
            }
        }
        return result;
    }

    private String resolveName(String rawName, Map<String, String> gnuNames) {
        if (rawName.startsWith("/") && rawName.substring(1).chars().allMatch(Character::isDigit)) {
            return gnuNames.getOrDefault(rawName.substring(1), rawName);
        }
        int slash = rawName.indexOf('/');
        if (slash >= 0) {
            return rawName.substring(0, slash);
        }
        return rawName;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private record RawMember(String headerName, int payloadOffset, byte[] payload) {
        ByteArrayOutputStream unused() {
            return new ByteArrayOutputStream();
        }
    }
}
