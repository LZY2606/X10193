package court.elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a GNU/SysV ar archive (little-endian symbol table + members) for fixtures.
 * The symbol table records the exported (defined) global names of each member.
 */
public class ArBuilder {

    public static final class Obj {
        final String name;
        final byte[] data;
        final List<String> defined = new ArrayList<>();
        public Obj(String name, byte[] data) { this.name = name; this.data = data; }
        public Obj defines(String name) { defined.add(name); return this; }
    }

    private final List<Obj> objs = new ArrayList<>();

    public ArBuilder add(String name, byte[] data, String... defined) {
        Obj o = new Obj(name, data);
        for (String d : defined) o.defines(d);
        objs.add(o);
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
            // Build symbol table first; member offsets are absolute within archive.
            // Reserve header (60) + count(4) + offsets; names appended afterwards.
            int count = (int) objs.stream().mapToLong(o -> o.defined.size()).sum();
            int symHeaderSize = 60;
            int symBodySize = 4 + count * 4;
            int namesBytes = 0;
            for (Obj o : objs) for (String s : o.defined) namesBytes += s.getBytes(StandardCharsets.UTF_8).length + 1;
            int symTotal = symBodySize + namesBytes;

            // First-pass: compute member offsets.
            long cursor = 8L + symHeaderSize + symTotal;
            long[] memberStart = new long[objs.size()];
            long[] dataStart = new long[objs.size()];
            for (int i = 0; i < objs.size(); i++) {
                if ((cursor & 1) == 1) cursor++;
                memberStart[i] = cursor;
                dataStart[i] = cursor + 60;
                cursor = dataStart[i] + objs.get(i).data.length;
            }

            // symbol table member "/"
            StringBuilder hdr = new StringBuilder();
            hdr.append(pad("/", 16)).append(pad("0", 12)).append(pad("0", 6)).append(pad("0", 6))
               .append(pad("100644", 8)).append(pad(String.valueOf(symTotal), 10))
               .append('`').append("\n");
            out.write(hdr.toString().getBytes(StandardCharsets.US_ASCII));
            // count
            write4(out, count);
            for (Obj o : objs) for (String s : o.defined) write4(out, (int) dataStart[objs.indexOf(o)]);
            for (Obj o : objs) for (String s : o.defined) {
                out.write(s.getBytes(StandardCharsets.UTF_8));
                out.write(0);
            }
            if ((symTotal & 1) == 1) out.write('\n');

            for (int i = 0; i < objs.size(); i++) {
                if ((out.size() & 1) == 1) out.write('\n');
                Obj o = objs.get(i);
                String m = pad(o.name + "/", 16) + pad("0", 12) + pad("0", 6) + pad("0", 6)
                        + pad("100644", 8) + pad(String.valueOf(o.data.length), 10)
                        + "`" + "\n";
                out.write(m.getBytes(StandardCharsets.US_ASCII));
                out.write(o.data);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static void write4(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
        out.write((v >>> 16) & 0xff);
        out.write((v >>> 24) & 0xff);
    }

    private static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.substring(0, n);
    }
}
