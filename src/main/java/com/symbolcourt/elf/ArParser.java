package com.symbolcourt.elf;

import com.symbolcourt.model.ArchiveFile;
import com.symbolcourt.model.ArchiveMember;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ArParser {

    private static final String MAGIC = "!<arch>\n";
    private final ElfParser elfParser = new ElfParser();

    public ArchiveFile parse(byte[] data, String displayName) {
        ArchiveFile ar = new ArchiveFile();
        ar.displayName = displayName;
        if (data == null || data.length < MAGIC.length()
                || !new String(data, 0, MAGIC.length(), StandardCharsets.US_ASCII).equals(MAGIC)) {
            ar.corrupt = true;
            ar.error = "not an ar archive";
            return ar;
        }
        try {
            parseBody(data, ar);
        } catch (RuntimeException e) {
            ar.corrupt = true;
            if (ar.error == null) ar.error = "malformed archive: " + e;
        }
        return ar;
    }

    private void parseBody(byte[] data, ArchiveFile ar) {
        int pos = MAGIC.length();
        String longNames = null;
        long longNamesBase = -1;
        while (pos + 60 <= data.length) {
            if (data[pos] == '\n') { pos++; continue; }
            String rawName = new String(data, pos, 16, StandardCharsets.US_ASCII).trim();
            long size;
            try {
                size = Long.parseLong(new String(data, pos + 48, 10, StandardCharsets.US_ASCII).trim());
            } catch (NumberFormatException e) {
                throw new ElfFormatException("bad archive member size at " + pos);
            }
            long dataStart = pos + 60L;
            if (size < 0 || dataStart + size > data.length) {
                throw new ElfFormatException("archive member exceeds file at " + pos);
            }
            byte[] body = new byte[(int) size];
            System.arraycopy(data, (int) dataStart, body, 0, (int) size);

            if (rawName.equals("/") || rawName.equals("/SYM64/")) {
                readSymbolIndex(ar, body, rawName.equals("/SYM64/"));
            } else if (rawName.equals("//")) {
                longNames = new String(body, StandardCharsets.US_ASCII);
                longNamesBase = 0;
            } else if (rawName.startsWith("/")) {
                String name = gnuLongName(longNames, parseOffset(rawName));
                addMember(ar, name, body);
            } else if (rawName.equals("/") || rawName.startsWith("#1/")) {
                int nlen = Integer.parseInt(rawName.substring(3).trim());
                if (nlen < 0 || nlen > body.length) throw new ElfFormatException("bad BSD long name");
                String name = new String(body, 0, nlen, StandardCharsets.US_ASCII);
                int zero = name.indexOf(0);
                if (zero >= 0) name = name.substring(0, zero);
                byte[] real = new byte[body.length - nlen];
                System.arraycopy(body, nlen, real, 0, real.length);
                addMember(ar, name.trim(), real);
            } else {
                addMember(ar, stripName(rawName), body);
            }
            pos = (int) (dataStart + size + (size & 1L));
        }
    }

    private int parseOffset(String rawName) {
        String t = rawName.substring(1).trim();
        return Integer.parseInt(t);
    }

    private String gnuLongName(String table, long offset) {
        if (table == null || offset < 0 || offset > table.length()) {
            return "<bad-longname>";
        }
        int end = table.indexOf('\n', (int) offset);
        if (end < 0) end = table.length();
        String n = table.substring((int) offset, end);
        if (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        return n.trim();
    }

    private String stripName(String name) {
        int slash = name.indexOf('/');
        if (slash >= 0) name = name.substring(0, slash);
        return name.trim();
    }

    private void addMember(ArchiveFile ar, String name, byte[] body) {
        ArchiveMember m = new ArchiveMember();
        m.name = name;
        m.data = body;
        m.offset = ar.members.size();
        if (looksLikeElf(body)) {
            m.elf = elfParser.parse(body, ar.displayName, ar.displayName + "(" + name + ")");
            m.corrupt = m.elf.corrupt;
            m.error = m.elf.error;
        } else {
            m.corrupt = true;
            m.error = "not an ELF object";
        }
        ar.members.add(m);
    }

    private boolean looksLikeElf(byte[] b) {
        return b != null && b.length >= 4 && b[0] == 0x7f && b[1] == 'E' && b[2] == 'L' && b[3] == 'F';
    }

    private void readSymbolIndex(ArchiveFile ar, byte[] body, boolean is64) {
        ByteReader r = new ByteReader(body, com.symbolcourt.model.Enums.Endian.BIG);
        if (body.length < 4) return;
        long count = r.u32(0) & 0xffffffffL;
        if (count > 1_000_000L) return;
        int[] offsets = new int[(int) count];
        int p = 4;
        for (int i = 0; i < count; i++) {
            if (is64) {
                if (p + 8 > body.length) return;
                offsets[i] = (int) r.u64(p);
                p += 8;
            } else {
                if (p + 4 > body.length) return;
                offsets[i] = (int) r.u32(p);
                p += 4;
            }
        }
        int[] nameEnds = new int[(int) count];
        for (int i = 0; i < count; i++) {
            int start = p;
            while (p < body.length && body[p] != 0) p++;
            nameEnds[i] = start;
            if (p < body.length) p++;
        }
        for (int i = 0; i < count; i++) {
            int end = nameEnds[i];
            int stop = end;
            while (stop < body.length && body[stop] != 0) stop++;
            String name = new String(body, end, stop - end, StandardCharsets.US_ASCII);
            ar.symbolIndex.computeIfAbsent(name, k -> new ArrayList<>()).add(offsets[i]);
        }
    }

    public List<String> memberNames(ArchiveFile ar) {
        List<String> names = new ArrayList<>();
        ar.members.forEach(m -> names.add(m.name));
        return names;
    }
}
