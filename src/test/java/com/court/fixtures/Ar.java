package com.court.fixtures;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a SysV/GNU static archive (ar format) with symbol + long-name tables. */
public class Ar {

    public static class Member {
        public final String name;
        public final byte[] data;
        public Member(String name, byte[] data) { this.name = name; this.data = data; }
    }

    private final List<Member> members = new ArrayList<>();
    private final Map<String, List<String>> exports = new LinkedHashMap<>();

    public Ar member(String name, byte[] data) {
        members.add(new Member(name, data));
        return this;
    }

    /** Declare symbols exported by a member (drives the archive symbol table). */
    public Ar exports(String memberName, String... names) {
        exports.computeIfAbsent(memberName, k -> new ArrayList<>());
        for (String n : names) if (!exports.get(memberName).contains(n)) exports.get(memberName).add(n);
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));

        // Symbol table content: big-endian count + member header offsets + strings.
        ByteArrayOutputStream symBody = new ByteArrayOutputStream();
        List<String> orderedSyms = new ArrayList<>();
        Map<String, Integer> ordinalByMember = new LinkedHashMap<>();
        int ord = 0;
        for (Member m : members) { ordinalByMember.put(m.name, ord); ord++; }
        // placeholder count, offsets patched after header positions known
        int count = 0;
        for (Member m : members) {
            List<String> ex = exports.getOrDefault(m.name, List.of());
            count += ex.size();
        }
        // long name table (// member) required when any name >15 chars
        boolean needLongNames = members.stream().anyMatch(m -> m.name.length() > 15);
        ByteArrayOutputStream longNames = new ByteArrayOutputStream();
        Map<String, Integer> longOffsets = new LinkedHashMap<>();
        if (needLongNames) {
            for (Member m : members) {
                if (m.name.length() > 15) {
                    longOffsets.put(m.name, longNames.size());
                    longNames.writeBytes(m.name.getBytes(StandardCharsets.US_ASCII));
                    longNames.write('/');
                    longNames.write('\n');
                }
            }
        }

        // Positions: magic(8) + symHeader(60) + symBody + optional longNameHeader+body + members
        long symBodySize = 4L + 4L * count;
        // string table size
        ByteArrayOutputStream symStrings = new ByteArrayOutputStream();
        for (Member m : members) {
            for (String s : exports.getOrDefault(m.name, List.of())) orderedSyms.add(s);
        }
        for (String s : orderedSyms) {
            symStrings.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
            symStrings.write(0);
        }
        symBodySize += symStrings.size();

        long pos = 8;
        long symHeaderPos = pos;
        long symDataStart = pos + 60;
        pos = symDataStart + symBodySize;
        if ((symBodySize & 1) == 1) pos++;
        long longNameDataStart = -1;
        if (needLongNames) {
            pos += 60;
            longNameDataStart = pos;
            pos += longNames.size();
            if ((longNames.size() & 1) == 1) pos++;
        }
        Map<String, Long> headerPos = new LinkedHashMap<>();
        for (Member m : members) {
            headerPos.put(m.name, pos);
            pos += 60 + m.data.length;
            if ((m.data.length & 1) == 1) pos++;
        }

        // write symbol header
        writeHeader(out, "/", symBodySize);
        // count
        writeBE4(symBody, count);
        for (Member m : members) {
            for (String s : exports.getOrDefault(m.name, List.of())) {
                writeBE4(symBody, headerPos.get(m.name));
            }
        }
        symBody.writeBytes(symStrings.toByteArray());
        out.writeBytes(symBody.toByteArray());
        if ((symBody.size() & 1) == 1) out.write('\n');

        if (needLongNames) {
            writeHeader(out, "//", longNames.size());
            out.writeBytes(longNames.toByteArray());
            if ((longNames.size() & 1) == 1) out.write('\n');
        }
        for (Member m : members) {
            String hdrName = m.name.length() > 15
                    ? ("/" + longOffsets.get(m.name) + spacePad("", 14))
                    : (m.name + "/");
            writeHeader(out, hdrName, m.data.length);
            out.writeBytes(m.data);
            if ((m.data.length & 1) == 1) out.write('\n');
        }
        return out.toByteArray();
    }

    private static void writeHeader(ByteArrayOutputStream out, String name, long size) {
        StringBuilder h = new StringBuilder();
        h.append(rightPad(name, 16));
        h.append(rightPad("0", 12));   // mtime
        h.append(rightPad("0", 6));    // uid
        h.append(rightPad("0", 6));    // gid
        h.append(rightPad("100644", 8));
        h.append(rightPad(Long.toString(size), 10));
        h.append("`\n");
        out.writeBytes(h.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static String rightPad(String s, int n) {
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }

    private static String spacePad(String s, int n) { return rightPad(s, n); }

    private static void writeBE4(ByteArrayOutputStream b, long v) {
        b.write((int) ((v >>> 24) & 0xff));
        b.write((int) ((v >>> 16) & 0xff));
        b.write((int) ((v >>> 8) & 0xff));
        b.write((int) (v & 0xff));
    }
}
