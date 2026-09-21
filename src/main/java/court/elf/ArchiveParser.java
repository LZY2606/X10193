package court.elf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GNU/BSD ar 归档解析。只做读取，不调用 ar 工具。
 * 支持：
 *  - 全局魔数 "!<arch>\n"
 *  - "/" 符号索引成员（GNU: 名字数表+偏移表；BSD: __.SYMDEF 风格不支持时跳过）
 *  - "//" GNU 长名表
 *  - "/<n>" 长名引用
 *  - thin archive（"!<thin>\n"）显式拒绝
 * 每个成员返回名字与原始字节，长度/魔数/偏移全部边界校验。
 */
public final class ArchiveParser {

    public static final class Member {
        public final String name;
        public final byte[] data;
        public final int rawIndex;

        Member(String name, byte[] data, int rawIndex) {
            this.name = name;
            this.data = data;
            this.rawIndex = rawIndex;
        }
    }

    public static boolean isArchive(byte[] data) {
        return data != null && data.length >= 8
                && data[0] == '!' && data[1] == '<'
                && (data[2] == 'a' || data[2] == 't')
                && data[3] == 'r' && data[4] == 'c' && data[5] == 'h'
                && data[6] == '>' && data[7] == '\n';
    }

    public static List<Member> parse(byte[] data) {
        if (data == null || data.length < 8) {
            throw new ElfException("archive 过短");
        }
        if (data[0] != '!' || data[1] != '<'
                || !((data[2] == 'a' && data[3] == 'r' && data[4] == 'c' && data[5] == 'h')
                     || (data[2] == 't' && data[3] == 'h' && data[4] == 'i' && data[5] == 'n'))
                || data[6] != '>' || data[7] != '\n') {
            throw new ElfException("archive 魔数不匹配");
        }
        if (data[2] == 't') {
            throw new ElfException("thin archive 不支持（成员不在归档内）");
        }
        ByteReader br = new ByteReader(data, true);
        long pos = 8;
        byte[] longNames = null;
        Map<String, List<Long>> symbolIndex = new LinkedHashMap<>();
        List<Member> members = new ArrayList<>();
        int memberOrdinal = 0;
        while (pos < data.length) {
            if (pos + 60 > data.length) {
                throw new ElfException("archive 成员头被截断 @ " + pos);
            }
            String rawName = new String(data, (int) pos, 16, StandardCharsets.US_ASCII).trim();
            long size = parseDecimal(data, pos + 48, 10, "成员大小");
            if (size < 0 || pos + 60 + size > data.length) {
                throw new ElfException("archive 成员数据越界: " + rawName + " size=" + size);
            }
            String end = new String(data, (int) pos + 58, 2, StandardCharsets.US_ASCII);
            if (!end.equals("`\n")) {
                throw new ElfException("archive 成员头结束标记损坏 @ " + pos);
            }
            long dataStart = pos + 60;
            byte[] body = br.slice(dataStart, size);
            String name = rawName;
            boolean special = false;
            if (rawName.equals("/") || rawName.startsWith("/SYM64/")) {
                special = true;
                parseSymbolIndex(body, members, symbolIndex);
            } else if (rawName.equals("//")) {
                longNames = body;
                special = true;
            } else if (rawName.startsWith("/") && !rawName.startsWith("/ ")) {
                long idx = parseLongNameIndex(rawName);
                name = readLongName(longNames, idx);
            } else {
                int slash = rawName.indexOf('/');
                if (slash >= 0) {
                    name = rawName.substring(0, slash);
                }
                // BSD 风格 "#1/n" 名内联
                if (name.startsWith("#1/")) {
                    byte[] nbuf = name.substring(3).getBytes(StandardCharsets.US_ASCII);
                    int nlen = (int) parseDecimal(nbuf, 0, nbuf.length, "BSD 名长度");
                    if (nlen > body.length) {
                        throw new ElfException("BSD archive 内联名越界");
                    }
                    name = new String(body, 0, nlen, StandardCharsets.US_ASCII).replace("\0", "").trim();
                    byte[] trimmed = new byte[body.length - nlen];
                    System.arraycopy(body, nlen, trimmed, 0, trimmed.length);
                    body = trimmed;
                }
            }
            if (!special) {
                members.add(new Member(name, body, memberOrdinal++));
            }
            long advance = 60 + size;
            if (size % 2 != 0) {
                advance++; // 偶数对齐填充
            }
            pos += advance;
        }
        return members;
    }

    private static long parseLongNameIndex(String rawName) {
        try {
            return Long.parseLong(rawName.substring(1).trim());
        } catch (NumberFormatException e) {
            throw new ElfException("非法长名引用: " + rawName);
        }
    }

    private static String readLongName(byte[] longNames, long idx) {
        if (longNames == null || idx < 0 || idx >= longNames.length) {
            throw new ElfException("长名表索引越界: " + idx);
        }
        int end = (int) idx;
        while (end < longNames.length && longNames[end] != '\n' && longNames[end] != '/') {
            end++;
        }
        return new String(longNames, (int) idx, end - (int) idx, StandardCharsets.UTF_8).trim();
    }

    private static void parseSymbolIndex(byte[] body, List<Member> members,
                                         Map<String, List<Long>> index) {
        if (body.length < 4) {
            return;
        }
        ByteReader br = new ByteReader(body, true);
        long count = br.u32(0) & 0xffffffffL;
        if (count > body.length / 4L) {
            throw new ElfException("archive 符号索引数量异常: " + count);
        }
        // 偏移表
        long[] offsets = new long[(int) count];
        for (int i = 0; i < count; i++) {
            offsets[i] = br.u32(4 + 4L * i);
        }
        long strStart = 4 + 4 * count;
        for (int i = 0; i < count; i++) {
            int e = (int) strStart;
            while (e < body.length && body[e] != 0) {
                e++;
            }
            if (e >= body.length) {
                throw new ElfException("archive 符号索引字符串越界");
            }
            String sym = new String(body, (int) strStart, e - (int) strStart, StandardCharsets.UTF_8);
            index.computeIfAbsent(sym, k -> new ArrayList<>()).add(offsets[i]);
            strStart = e + 1;
        }
    }

    private static long parseDecimal(byte[] buf, long off, int len, String what) {
        long v = 0;
        boolean any = false;
        for (int i = 0; i < len; i++) {
            byte c = buf[(int) (off + i)];
            if (c == ' ' || c == '`') {
                if (any) {
                    break;
                }
                continue;
            }
            if (c < '0' || c > '9') {
                if (any) {
                    break;
                }
                throw new ElfException(what + " 不是十进制数字");
            }
            v = v * 10 + (c - '0');
            if (v > 0x7fffffffffffffffL) {
                throw new ElfException(what + " 过大");
            }
            any = true;
        }
        if (!any) {
            throw new ElfException(what + " 缺失");
        }
        return v;
    }

    private ArchiveParser() {
    }
}
