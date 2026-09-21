package court.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GNU ar 归档构造器（fixture 用）。布局：
 *   魔数 + "/" 符号索引成员 + "//" 长名表 + 各成员（奇数填充换行）。
 */
public final class ArchiveBuilder {

    private final List<Member> members = new ArrayList<>();

    public ArchiveBuilder member(String name, byte[] elf) {
        members.add(new Member(name, elf));
        return this;
    }

    private record Member(String name, byte[] data) {
    }

    public byte[] build() {
        try {
            return doBuild();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] doBuild() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));

        // 长名表
        ByteArrayOutputStream longNames = new ByteArrayOutputStream();
        List<Integer> nameOffsets = new ArrayList<>();
        for (Member m : members) {
            nameOffsets.add(longNames.size());
            longNames.writeBytes(m.name.getBytes(StandardCharsets.UTF_8));
            longNames.write('/');
            longNames.write('\n');
        }
        byte[] ln = longNames.toByteArray();

        // 先计算成员数据的文件偏移（从魔数 8 起）
        // "/" 符号索引头+内容；"//" 长名头+内容；然后每个成员
        // 符号索引：4 字节 count + count*4 offsets + 每个符号名 NUL。
        // 符号表符号使用每个 ELF 里第一个全局定义名——简化：让调用方通过 nameMap 提供。
        List<String> indexSyms = new ArrayList<>();
        for (Member m : members) {
            // 约定：成员名即其提供的主符号（测试中 a.o->a 等），用成员 base 名
            String base = m.name.contains(".") ? m.name.substring(0, m.name.indexOf('.')) : m.name;
            indexSyms.add(base);
        }
        ByteArrayOutputStream symIndex = new ByteArrayOutputStream();
        w32(symIndex, members.size());
        // 偏移稍后回填：先占位
        int[] offPos = new int[members.size()];
        for (int i = 0; i < members.size(); i++) {
            offPos[i] = symIndex.size();
            w32(symIndex, 0);
        }
        int[] strStart = new int[members.size()];
        for (int i = 0; i < members.size(); i++) {
            strStart[i] = symIndex.size();
            symIndex.writeBytes(indexSyms.get(i).getBytes(StandardCharsets.UTF_8));
            symIndex.write(0);
        }
        byte[] si = symIndex.toByteArray();

        long cursor = 8L;
        cursor += 60 + si.length + (si.length % 2 != 0 ? 1 : 0);
        cursor += 60 + ln.length + (ln.length % 2 != 0 ? 1 : 0);
        long[] memberOff = new long[members.size()];
        for (int i = 0; i < members.size(); i++) {
            memberOff[i] = cursor;
            cursor += 60 + members.get(i).data.length
                    + (members.get(i).data.length % 2 != 0 ? 1 : 0);
        }
        for (int i = 0; i < members.size(); i++) {
            put32At(si, offPos[i], (int) memberOff[i]);
        }

        writeHeader(out, "/", si.length, 0, 0, 0, 0);
        out.writeBytes(si);
        if (si.length % 2 != 0) out.write('\n');

        writeHeader(out, "//", ln.length, 0, 0, 0, 0);
        out.writeBytes(ln);
        if (ln.length % 2 != 0) out.write('\n');

        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            String nameField = "/" + nameOffsets.get(i);
            writeHeader(out, nameField, m.data.length, 0, 0, 0100644, 0);
            out.writeBytes(m.data);
            if (m.data.length % 2 != 0) out.write('\n');
        }
        return out.toByteArray();
    }

    private void writeHeader(ByteArrayOutputStream out, String name, long size,
                             int mtime, int uid, int gid, int mode) {
        StringBuilder h = new StringBuilder();
        h.append(pad(name, 16));
        h.append(pad(String.valueOf(mtime), 12));
        h.append(pad(String.valueOf(uid), 6));
        h.append(pad(String.valueOf(gid), 6));
        h.append(pad(String.format("%o", mode), 8));
        h.append(pad(String.valueOf(size), 10));
        h.append("`\n");
        out.writeBytes(h.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static String pad(String s, int n) {
        if (s.length() >= n) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    private static void w32(ByteArrayOutputStream o, long v) {
        o.write((int) (v & 0xff)); o.write((int) ((v >>> 8) & 0xff));
        o.write((int) ((v >>> 16) & 0xff)); o.write((int) ((v >>> 24) & 0xff));
    }

    private static void put32At(byte[] a, int off, int v) {
        a[off] = (byte) v; a[off + 1] = (byte) (v >>> 8);
        a[off + 2] = (byte) (v >>> 16); a[off + 3] = (byte) (v >>> 24);
    }
}
