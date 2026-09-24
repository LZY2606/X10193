package com.symbolcourt.elf;

import com.symbolcourt.model.Models.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal reader for Unix ar(5) archives as produced by GNU/BSD ar.
 * GNU symbol and extended-name tables ("//") are recognized and skipped;
 * object members are returned with explicit member indexes so tie-breaking
 * never depends on a filesystem walk.
 */
public class ArParser {

    public boolean isAr(byte[] data) {
        return data != null && data.length >= 8
                && new String(data, 0, 8, StandardCharsets.US_ASCII).equals("!<arch>\n");
    }

    public List<Member> members(byte[] data) {
        if (!isAr(data)) throw new ElfReadException("not an ar archive");
        List<Member> out = new ArrayList<>();
        String longNames = null;
        long pos = 8;
        int idx = 0;
        while (pos + 60 <= data.length) {
            String rawName = new String(data, (int) pos, 16, StandardCharsets.US_ASCII).trim();
            long size = parseLong(data, pos + 48, 10);
            long dataStart = pos + 60;
            if (size < 0 || dataStart + size > data.length) {
                throw new ElfReadException("ar member " + rawName + " exceeds archive bounds");
            }
            String name = rawName;
            boolean special = false;
            if (name.equals("/") || name.equals("/SYM64/") || name.equals("__.SYMDEF")) {
                special = true;
            } else if (name.equals("//")) {
                longNames = new String(data, (int) dataStart, (int) size, StandardCharsets.US_ASCII);
                special = true;
            } else if (name.startsWith("/")) {
                int off = Integer.parseInt(name.substring(1).trim());
                name = gnuLongName(longNames, off);
            } else {
                int slash = name.indexOf('/');
                if (slash >= 0) name = name.substring(0, slash);
            }
            if (!special) {
                Member m = new Member();
                m.name = name;
                m.index = idx++;
                m.data = Arrays.copyOfRange(data, (int) dataStart, (int) (dataStart + size));
                out.add(m);
            }
            pos = dataStart + size;
            if ((size & 1) == 1) pos++; // newline padding
        }
        return out;
    }

    private String gnuLongName(String table, int off) {
        if (table == null || off < 0 || off >= table.length()) return "member_" + off;
        int end = off;
        while (end < table.length() && table.charAt(end) != '/' && table.charAt(end) != '\n') end++;
        return table.substring(off, end).trim();
    }

    private long parseLong(byte[] d, int off, int len) {
        long v = 0;
        boolean seen = false;
        for (int i = 0; i < len; i++) {
            byte b = d[off + i];
            if (b >= '0' && b <= '9') { v = v * 10 + (b - '0'); seen = true; }
            else if (seen) break;
        }
        return v;
    }

    public static class Member {
        public String name;
        public int index;
        public byte[] data;
    }
}
