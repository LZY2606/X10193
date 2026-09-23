package com.symbolcourt.ar;

import com.symbolcourt.elf.ElfException;
import com.symbolcourt.elf.ElfParser;
import com.symbolcourt.model.ElfFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GNU/SysV ar(1) archive 读取器。支持：
 * - "/" 符号查找表（仅跳过，不参与成员名）
 * - "//" GNU 长名表
 * - 成员名带斜杠尾缀与 "/123" 长名引用
 */
public class ArReader {

    private static final String MAGIC = "!<arch>\n";

    public static boolean isArchive(byte[] data) {
        if (data == null || data.length < MAGIC.length()) {
            return false;
        }
        for (int i = 0; i < MAGIC.length(); i++) {
            if (data[i] != (byte) MAGIC.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public List<ArMember> read(byte[] data) throws ArException {
        if (!isArchive(data)) {
            throw new ArException("缺少 ar 魔数 \"!<arch>\\n\"");
        }
        List<ArMember> members = new ArrayList<>();
        int p = MAGIC.length();
        String longNames = null;
        while (p < data.length) {
            if (p + 60 > data.length) {
                throw new ArException("成员头被截断于 offset=" + p);
            }
            String rawName = new String(data, p, 16, StandardCharsets.US_ASCII).trim();
            String sizeField = new String(data, p + 48, 10, StandardCharsets.US_ASCII).trim();
            long size;
            try {
                size = Long.parseLong(sizeField);
            } catch (NumberFormatException e) {
                throw new ArException("成员大小不是数字: \"" + sizeField + "\"");
            }
            if (size < 0 || p + 60L + size > data.length) {
                throw new ArException("成员数据越界: " + rawName + " size=" + size);
            }
            long dataOffset = p + 60L;
            if ("/".equals(rawName) || "/SYM64/".equals(rawName)) {
                // 符号查找表，跳过
            } else if ("//".equals(rawName)) {
                longNames = new String(data, (int) dataOffset, (int) size, StandardCharsets.UTF_8);
            } else {
                ArMember m = new ArMember();
                m.size = size;
                m.dataOffset = dataOffset;
                m.name = resolveName(rawName, longNames);
                m.data = new byte[(int) size];
                System.arraycopy(data, (int) dataOffset, m.data, 0, (int) size);
                members.add(m);
            }
            long total = 60 + size;
            p += total;
            if ((total & 1) == 1) {
                if (p >= data.length || data[p] != '\n') {
                    // 填充缺失：宽容结束
                    break;
                }
                p++;
            }
        }
        return members;
    }

    private String resolveName(String rawName, String longNames) throws ArException {
        if (rawName.endsWith("/")) {
            return rawName.substring(0, rawName.length() - 1);
        }
        int slash = rawName.indexOf('/');
        if (slash >= 0) {
            String numStr = rawName.substring(slash + 1);
            try {
                int idx = Integer.parseInt(numStr);
                if (longNames == null || idx < 0 || idx >= longNames.length()) {
                    throw new ArException("长名索引越界: " + idx);
                }
                int end = idx;
                while (end < longNames.length() && longNames.charAt(end) != '\n'
                        && longNames.charAt(end) != '/') {
                    end++;
                }
                return longNames.substring(idx, end).trim();
            } catch (NumberFormatException e) {
                throw new ArException("无法解析成员名: " + rawName);
            }
        }
        return rawName;
    }

    /** 解析成员内部的 ELF，失败时异常上抛由调用方隔离（不影响其他成员）。 */
    public ElfFile parseMember(ArMember member, String displayName) throws ElfException {
        return new ElfParser().parse(member.data, displayName);
    }
}
