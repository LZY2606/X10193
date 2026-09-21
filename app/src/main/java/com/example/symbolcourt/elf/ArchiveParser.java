package com.example.symbolcourt.elf;

import com.example.symbolcourt.domain.Models.ArchiveFile;
import com.example.symbolcourt.domain.Models.ArchiveMember;
import com.example.symbolcourt.domain.Models.ParsedObject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArchiveParser {
    private static final byte[] MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    private final ElfParser elfParser;

    public ArchiveParser(ElfParser elfParser) {
        this.elfParser = elfParser;
    }

    public ArchiveFile parse(String archiveId, String displayName, String path, byte[] data) {
        if (!isArchive(data)) {
            return new ArchiveFile(archiveId, displayName, path, List.of(), "不是 ar archive");
        }
        List<RawMember> rawMembers = new ArrayList<>();
        try {
            parseRawMembers(data, rawMembers);
        } catch (Exception ex) {
            return new ArchiveFile(archiveId, displayName, path, List.of(),
                    "损坏 archive：" + ex.getMessage());
        }
        String longNames = rawMembers.stream()
                .filter(member -> member.name.equals("//"))
                .map(member -> new String(member.content, StandardCharsets.UTF_8))
                .findFirst()
                .orElse("");
        List<ArchiveMember> members = new ArrayList<>();
        int memberIndex = 0;
        for (RawMember raw : rawMembers) {
            String memberName = memberName(raw.name, longNames);
            if (memberName == null || memberName.equals("/") || memberName.equals("/SYM64/")
                    || memberName.startsWith("__.SYMDEF")) {
                continue;
            }
            String objectId = archiveId + "!" + memberIndex + ":" + memberName;
            ParsedObject object = elfParser.parse(objectId, displayName + "(" + memberName + ")",
                    displayName, memberName, path + "!" + memberName, raw.content);
            members.add(new ArchiveMember(archiveId, displayName, memberName, raw.offset, object));
            memberIndex++;
        }
        return new ArchiveFile(archiveId, displayName, path, List.copyOf(members), null);
    }

    public boolean isArchive(byte[] data) {
        if (data == null || data.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private void parseRawMembers(byte[] data, List<RawMember> members) throws Exception {
        int offset = MAGIC.length;
        while (offset < data.length) {
            if (offset + 60 > data.length) {
                throw new IllegalArgumentException("archive header 被截断");
            }
            String header = new String(data, offset, 60, StandardCharsets.US_ASCII);
            if (!header.startsWith("`\n", 58)) {
                throw new IllegalArgumentException("archive magic 终止符非法");
            }
            String rawName = header.substring(0, 16).trim();
            long size = Long.parseLong(header.substring(48, 58).trim());
            long contentOffset = offset + 60L;
            if (size < 0 || contentOffset > data.length || size > data.length - contentOffset) {
                throw new IllegalArgumentException("archive member 越界");
            }
            byte[] content = new byte[(int) size];
            System.arraycopy(data, (int) contentOffset, content, 0, (int) size);
            members.add(new RawMember(rawName, offset, content));
            offset = (int) (contentOffset + size);
            if ((size & 1) == 1) {
                if (offset >= data.length || data[offset] != '\n') {
                    throw new IllegalArgumentException("archive padding 缺失");
                }
                offset++;
            }
        }
    }

    private String memberName(String rawName, String longNames) {
        try {
            if (rawName.endsWith("/")) {
                return rawName.substring(0, rawName.length() - 1);
            }
            if (rawName.startsWith("/")) {
                int longOffset = Integer.parseInt(rawName.substring(1));
                if (longOffset < 0 || longOffset >= longNames.length()) {
                    return null;
                }
                int end = longNames.indexOf('/', longOffset);
                if (end < 0) {
                    end = longNames.indexOf('\n', longOffset);
                }
                if (end < 0) {
                    return null;
                }
                return longNames.substring(longOffset, end).trim();
            }
            int slash = rawName.indexOf('/');
            return slash < 0 ? rawName.trim() : rawName.substring(0, slash);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record RawMember(String name, long offset, byte[] content) {
    }
}
