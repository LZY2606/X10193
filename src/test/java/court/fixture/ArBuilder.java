package court.fixture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds GNU-style ar(1) archives (with // long-name table) for tests. */
public final class ArBuilder {

    private final Map<String, byte[]> members = new LinkedHashMap<>();

    public ArBuilder add(String name, byte[] data) {
        members.put(name, data);
        return this;
    }

    public byte[] build() {
        ByteArray2 out = new ByteArray2();
        out.ascii("!<arch>\n");
        // GNU symbol index "/" member: count then offsets then name table.
        List<String> names = new ArrayList<>(members.keySet());
        // Build long-name table
        ByteArray2 longNames = new ByteArray2();
        Map<String, Integer> longOff = new LinkedHashMap<>();
        boolean anyLong = names.stream().anyMatch(n -> n.length() > 15);
        for (String n : names) {
            longOff.put(n, longNames.size());
            longNames.ascii(n + "/\n");
        }
        // Symbol index member
        // Collect one defined global per member for the index (loader ignores index).
        ByteArray2 symBody = new ByteArray2();
        List<Integer> offsets = new ArrayList<>();
        ByteArray2 nameTab = new ByteArray2();
        symBody.write4le(names.size());
        int dataStart;
        // placeholder offsets computed relative to archive; write zeros then patch
        int offPlaceholderStart = symBody.size();
        for (int i = 0; i < names.size(); i++) symBody.write4le(0);
        for (String n : names) {
            nameTab.ascii(n + "\0");
        }
        symBody.bytes(nameTab.toByteArray());

        // long name member
        int memberDataCursor;
        // First emit sym index member
        byte[] sym = symBody.toByteArray();
        memberDataCursor = 8 + 60 + sym.length + pad(sym.length);
        int longHeaderLen = 0, longDataLen = 0;
        if (anyLong) {
            longDataLen = longNames.size();
            longHeaderLen = 60;
        }
        int cur = memberDataCursor + longHeaderLen + longDataLen + pad(longDataLen);
        for (int i = 0; i < names.size(); i++) {
            offsets.add(cur);
            int len = members.get(names.get(i)).length;
            cur += 60 + len + pad(len);
        }
        // patch offsets
        byte[] symb = sym;
        for (int i = 0; i < offsets.size(); i++) {
            int v = offsets.get(i);
            symb[offPlaceholderStart + i * 4] = (byte) v;
            symb[offPlaceholderStart + i * 4 + 1] = (byte) (v >>> 8);
            symb[offPlaceholderStart + i * 4 + 2] = (byte) (v >>> 16);
            symb[offPlaceholderStart + i * 4 + 3] = (byte) (v >>> 24);
        }
        emitHeader(out, "/", symb.length, 0);
        out.bytes(symb);
        if ((symb.length & 1) == 1) out.ascii("\n");
        if (anyLong) {
            byte[] ln = longNames.toByteArray();
            emitHeader(out, "//", ln.length, 0);
            out.bytes(ln);
            if ((ln.length & 1) == 1) out.ascii("\n");
        }
        for (String n : names) {
            byte[] data = members.get(n);
            String hdrName = n.length() <= 15 ? padRight(n + "/", 16)
                    : padRight("/" + longOff.get(n), 16);
            emitHeader(out, hdrName, data.length, 1);
            out.bytes(data);
            if ((data.length & 1) == 1) out.ascii("\n");
        }
        return out.toByteArray();
    }

    private static int pad(int len) {
        return (len & 1) == 1 ? 1 : 0;
    }

    private static String padRight(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    private void emitHeader(ByteArray2 out, String nameField, int size, int mode) {
        out.ascii(padRight(nameField, 16));
        out.ascii(padRight("0", 12));   // mtime
        out.ascii(padRight("0", 6));    // uid
        out.ascii(padRight("0", 6));    // gid
        out.ascii(padRight("100644", 8)); // mode
        out.ascii(padRight(String.valueOf(size), 10));
        out.ascii("`\n");
    }

    private static final class ByteArray2 {
        private final java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        void ascii(String s) { o.writeBytes(s.getBytes(StandardCharsets.US_ASCII)); }
        void bytes(byte[] b) { o.writeBytes(b); }
        int size() { return o.size(); }
        byte[] toByteArray() { return o.toByteArray(); }
        void write4le(int v) {
            o.write(v & 0xff); o.write((v >>> 8) & 0xff);
            o.write((v >>> 16) & 0xff); o.write((v >>> 24) & 0xff);
        }
    }
}
