package court.symbol.fixture;

import court.symbol.elf.Elf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds minimal-but-valid ELF64 little-endian objects in-memory:
 * ET_REL with .symtab + RELA, ET_DYN with .dynsym + version sections,
 * COMDAT section groups, and ar archives wrapping them. No system tools used.
 */
public final class ElfFixture {

    public static final int MACHINE_X86_64 = 62;
    public static final int MACHINE_AARCH64 = 183;

    public static final int R_COPY = Elf.R_X86_64_COPY;
    public static final int R_GLOB_DAT = Elf.R_X86_64_GLOB_DAT;
    public static final int R_JUMP_SLOT = Elf.R_X86_64_JUMP_SLOT;

    public static final class Sym {
        String name;
        int binding = Elf.STB_GLOBAL;
        int type = Elf.STT_FUNC;
        int visibility = Elf.STV_DEFAULT;
        int shndx = 1;
        long value;
        long size;
        int versym = 1;
        boolean defined = true;

        public static Sym undef(String name) {
            Sym s = new Sym();
            s.name = name;
            s.shndx = 0;
            s.defined = false;
            return s;
        }

        public static Sym def(String name, int section) {
            Sym s = new Sym();
            s.name = name;
            s.shndx = section;
            return s;
        }

        public Sym binding(int b) { this.binding = b; return this; }
        public Sym type(int t) { this.type = t; return this; }
        public Sym vis(int v) { this.visibility = v; return this; }
        public Sym weak() { this.binding = Elf.STB_WEAK; return this; }
        public Sym local() { this.binding = Elf.STB_LOCAL; return this; }
        public Sym hidden() { this.visibility = Elf.STV_HIDDEN; return this; }
        public Sym protectedVis() { this.visibility = Elf.STV_PROTECTED; return this; }
        public Sym common(long align) {
            this.shndx = Elf.SHN_COMMON;
            this.type = Elf.STT_COMMON;
            this.value = align;
            return this;
        }
        public Sym version(int v) { this.versym = v; return this; }
        public Sym object(long sz) { this.type = Elf.STT_OBJECT; this.size = sz; return this; }
    }

    public record Rela(int symIndex, int type, long offset, long addend) {
    }

    public static final class Builder {
        private int type = Elf.ET_REL;
        private int machine = MACHINE_X86_64;
        private final List<Sym> symbols = new ArrayList<>();
        private int localCount = 1;
        private final Map<Integer, List<Rela>> relaForSection = new LinkedHashMap<>();
        private final List<VerDef> verDefs = new ArrayList<>();
        private final List<VerNeed> verNeeds = new ArrayList<>();
        private final List<String> needed = new ArrayList<>();
        private String soname;
        private String comdatSignature;

        public Builder relocatable() { this.type = Elf.ET_REL; return this; }
        public Builder shared() { this.type = Elf.ET_DYN; return this; }
        public Builder machine(int m) { this.machine = m; return this; }
        public Builder soname(String s) { this.soname = s; return this; }
        public Builder needed(String n) { this.needed.add(n); return this; }

        public int addSymbol(Sym s) {
            if (s.binding == Elf.STB_LOCAL) {
                symbols.add(localCount, s);
                localCount++;
            } else {
                symbols.add(s);
            }
            return symbols.indexOf(s);
        }

        public int addUndef(String name) {
            return addSymbol(Sym.undef(name));
        }

        public int addUndef(String name, int version) {
            Sym s = Sym.undef(name);
            s.versym = version;
            return addSymbol(s);
        }

        public Builder addRela(int targetSection, Rela rela) {
            relaForSection.computeIfAbsent(targetSection, k -> new ArrayList<>())
                    .add(rela);
            return this;
        }

        public Builder verDef(int ndx, String name, List<String> aliases) {
            verDefs.add(new VerDef(ndx, name, aliases == null ? List.of() : aliases));
            return this;
        }

        public Builder verNeed(int ndx, String fromSoname, String versionName, boolean weak) {
            verNeeds.add(new VerNeed(ndx, fromSoname, versionName, weak));
            return this;
        }

        public Builder comdat(String signatureSymbol) {
            this.comdatSignature = signatureSymbol;
            return this;
        }

        public byte[] build() {
            return ElfAssembler.assemble(type, machine, symbols, localCount,
                    relaForSection, verDefs, verNeeds, needed, soname, comdatSignature);
        }
    }

    record VerDef(int ndx, String name, List<String> aliases) {
    }

    record VerNeed(int ndx, String fromSoname, String name, boolean weak) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static byte[] archive(Map<String, byte[]> members) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        for (Map.Entry<String, byte[]> e : members.entrySet()) {
            byte[] payload = e.getValue();
            String hdr = padName(e.getKey())
                    + pad("0", 12) + pad("0", 6) + pad("0", 6)
                    + pad("100644", 8)
                    + pad(String.valueOf(payload.length), 10) + "`\n";
            out.writeBytes(hdr.getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(payload);
            if ((payload.length & 1) == 1) {
                out.write('\n');
            }
        }
        return out.toByteArray();
    }

    private static String padName(String name) {
        if (name.length() > 15) {
            throw new IllegalArgumentException("fixture 仅支持短成员名(<=15): " + name);
        }
        StringBuilder sb = new StringBuilder(name).append('/');
        while (sb.length() < 16) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String pad(String value, int width) {
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
