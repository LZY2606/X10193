package com.tribunal.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** System V ar 归档解析（只读）。成员保持惰性：只在裁决需要时才解析成员 ELF。 */
public class Archive {
    public String name;
    public final List<Member> members = new ArrayList<>();

    public static class Member {
        public String name;
        public long offset;     // 数据在归档中的偏移
        public long size;
        public byte[] data;     // 成员原始字节（惰性解析的输入）
    }

    public static boolean isArchive(byte[] data) {
        if (data == null || data.length < 8) return false;
        byte[] magic = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 8; i++) if (data[i] != magic[i]) return false;
        return true;
    }

    public static Archive parse(byte[] data, String name) {
        if (!isArchive(data)) throw new ElfException("不是 ar 归档: " + name);
        Archive a = new Archive();
        a.name = name;
        long off = 8;
        String longNames = null;
        while (off < data.length) {
            if (off + 60 > data.length) throw new ElfException("ar 成员头越界: " + name);
            String hdr = new String(data, (int) off, 60, StandardCharsets.US_ASCII);
            if (hdr.charAt(58) != '`' || hdr.charAt(59) != '\n') {
                throw new ElfException("ar 成员头魔数错误: " + name + " @" + off);
            }
            String rawName = hdr.substring(0, 16).trim();
            long size;
            try {
                size = Long.parseLong(hdr.substring(48, 58).trim());
            } catch (NumberFormatException e) {
                throw new ElfException("ar 成员长度非法: " + name);
            }
            if (size < 0 || off + 60 + size > data.length) {
                throw new ElfException("ar 成员数据越界: " + name);
            }
            long dataOff = off + 60;
            if (rawName.equals("/") || rawName.equals("/SYM64/")) {
                // 符号索引：裁决器自行扫描成员，忽略 ranlib 索引
            } else if (rawName.equals("//")) {
                longNames = new String(data, (int) dataOff, (int) size, StandardCharsets.UTF_8);
            } else {
                Member m = new Member();
                m.offset = dataOff;
                m.size = size;
                if (rawName.startsWith("/")) {
                    if (longNames == null) throw new ElfException("长文件名表缺失: " + name);
                    int idx;
                    try {
                        idx = Integer.parseInt(rawName.substring(1));
                    } catch (NumberFormatException e) {
                        throw new ElfException("长文件名索引非法: " + rawName);
                    }
                    if (idx < 0 || idx >= longNames.length()) {
                        throw new ElfException("长文件名索引越界: " + idx);
                    }
                    int end = longNames.indexOf('\n', idx);
                    if (end < 0) end = longNames.length();
                    m.name = longNames.substring(idx, end).replaceAll("/$", "");
                } else {
                    m.name = rawName.replaceAll("/$", "");
                }
                byte[] buf = new byte[(int) size];
                System.arraycopy(data, (int) dataOff, buf, 0, (int) size);
                m.data = buf;
                a.members.add(m);
            }
            off = dataOff + size + (size % 2);
        }
        return a;
    }
}
