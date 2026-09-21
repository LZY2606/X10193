package com.example.symbolcourt.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ElfFixtureBuilder {
    public static final int ET_REL = 1;
    public static final int ET_DYN = 3;
    public static final int R_X86_64_PC32 = 2;
    public static final int R_X86_64_COPY = 5;
    public static final int R_X86_64_JUMP_SLOT = 7;

    private int elfType = ET_REL;
    private final List<SymbolSpec> symbols = new ArrayList<>();
    private final List<RelocationSpec> relocations = new ArrayList<>();
    private final Map<String, Integer> versionDefinitions = new LinkedHashMap<>();
    private final List<String> versionRequirements = new ArrayList<>();
    private final Map<String, Boolean> versionDefault = new LinkedHashMap<>();

    public ElfFixtureBuilder type(int type) {
        this.elfType = type;
        return this;
    }

    public ElfFixtureBuilder symbol(SymbolSpec symbol) {
        symbols.add(symbol);
        return this;
    }

    public ElfFixtureBuilder relocation(long offset, int symbol, int type, long addend) {
        relocations.add(new RelocationSpec(offset, symbol, type, addend));
        return this;
    }

    public ElfFixtureBuilder defineVersion(String name, int index, boolean isDefault) {
        versionDefinitions.put(name, index);
        versionDefault.put(name, isDefault);
        return this;
    }

    public ElfFixtureBuilder requireVersion(String name) {
        versionRequirements.add(name);
        return this;
    }

    public byte[] build() {
        symbols.add(0, new SymbolSpec("", null, SymbolSpec.Binding.LOCAL, SymbolSpec.Type.NOTYPE,
                SymbolSpec.Visibility.DEFAULT, 0, 0, 0, false, false, false));
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        text.writeBytes(new byte[16]);
        ByteArrayOutputStream shstr = sectionStringTable();
        ByteArrayOutputStream str = symbolStringTable();
        ByteArrayOutputStream sym = symbolTable(str);
        ByteArrayOutputStream rela = relocations();
        ByteArrayOutputStream verdef = verdef();
        ByteArrayOutputStream verneed = verneed();
        ByteArrayOutputStream versym = versym();

        List<RawSection> sections = new ArrayList<>();
        sections.add(new RawSection(0, "", 0, 0, new byte[0], 0, 0, 8));
        sections.add(new RawSection(1, ".text", 1, 6, text.toByteArray(), 0, 0, 0));
        sections.add(new RawSection(2, ".shstrtab", 3, 0, shstr.toByteArray(), 0, 0, 0));
        sections.add(new RawSection(3, ".strtab", 3, 0, str.toByteArray(), 0, 0, 0));
        sections.add(new RawSection(4, ".symtab", 2, 0, sym.toByteArray(), 3, 1, 24));
        if (!relocations.isEmpty()) {
            sections.add(new RawSection(5, ".rela.text", 4, 0, rela.toByteArray(), 4, 1, 24));
        }
        if (!versionDefinitions.isEmpty()) {
            sections.add(new RawSection(6, ".gnu.version_d", 0x6ffffffd, 0, verdef.toByteArray(),
                    3, versionDefinitions.size(), 0));
        }
        if (!versionRequirements.isEmpty()) {
            sections.add(new RawSection(7, ".gnu.version_r", 0x6ffffffe, 0, verneed.toByteArray(),
                    3, 1, 0));
        }
        if (!versionDefinitions.isEmpty() || !versionRequirements.isEmpty()) {
            sections.add(new RawSection(8, ".gnu.version", 0x6fffffff, 0, versym.toByteArray(),
                    4, 0, 2));
        }
        return link(sections);
    }

    private ByteArrayOutputStream sectionStringTable() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        for (String name : List.of(".text", ".shstrtab", ".strtab", ".symtab", ".rela.text",
                ".gnu.version_d", ".gnu.version_r", ".gnu.version")) {
            out.writeBytes(name.getBytes(StandardCharsets.UTF_8));
            out.write(0);
        }
        return out;
    }

    private ByteArrayOutputStream symbolStringTable() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        for (SymbolSpec symbol : symbols) {
            if (!symbol.name().isEmpty()) {
                out.writeBytes(symbol.name().getBytes(StandardCharsets.UTF_8));
                out.write(0);
            }
        }
        for (String extra : new LinkedHashSet<>(versionDefinitions.keySet())) {
            writeExtraVersionString(out, extra);
        }
        for (String extra : versionRequirements) {
            writeExtraVersionString(out, extra);
        }
        writeExtraVersionString(out, "libneeded.so");
        return out;
    }

    private void writeExtraVersionString(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        out.write(0);
    }

    private ByteArrayOutputStream symbolTable(ByteArrayOutputStream strings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, Integer> offsets = stringOffsets(strings.toByteArray());
        for (SymbolSpec symbol : symbols) {
            write32(out, offsets.getOrDefault(symbol.name(), 0));
            write8(out, symbol.binding().value << 4 | symbol.type().value);
            write8(out, symbol.visibility().value);
            write16(out, symbol.sectionIndex());
            write64(out, symbol.value());
            write64(out, symbol.size());
        }
        return out;
    }

    private ByteArrayOutputStream relocations() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (RelocationSpec relocation : relocations) {
            write64(out, relocation.offset());
            write64(out, (((long) relocation.symbolIndex()) << 32)
                    | (relocation.type() & 0xffffffffL));
            write64(out, relocation.addend());
        }
        return out;
    }

    private ByteArrayOutputStream versym() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, 0);
        for (int i = 1; i < symbols.size(); i++) {
            SymbolSpec symbol = symbols.get(i);
            if (symbol.version() == null || symbol.version().isBlank()) {
                write16(out, 1);
            } else if (versionDefinitions.containsKey(symbol.version())) {
                write16(out, versionDefinitions.get(symbol.version()));
            } else {
                write16(out, 2 + versionRequirements.indexOf(symbol.version()));
            }
        }
        return out;
    }

    private ByteArrayOutputStream verdef() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long base = 0;
        for (Map.Entry<String, Integer> entry : versionDefinitions.entrySet()) {
            int flags = versionDefault.getOrDefault(entry.getKey(), true) ? 1 : 0;
            write16(out, 1);
            write16(out, flags);
            write16(out, entry.getValue());
            write16(out, 1);
            write32(out, 0);
            write32(out, 0);
            write32(out, 20);
            write32(out, 0);
            write32(out, versionStringOffset(entry.getKey(), strOffsets()));
            write32(out, 0);
        }
        return out;
    }

    private ByteArrayOutputStream verneed() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int other = 2;
        for (String version : versionRequirements) {
            write16(out, 1);
            write16(out, 1);
            write16(out, 1);
            write16(out, 16);
            write32(out, versionStringOffset("libneeded.so", strOffsets()));
            write32(out, 16);
            write32(out, 0);
            write32(out, other++);
            write32(out, versionStringOffset(version, strOffsets()));
            write32(out, 0);
            write32(out, 1);
        }
        return out;
    }

    private Map<String, Integer> strOffsets() {
        return stringOffsets(symbolStringTable().toByteArray());
    }

    private int versionStringOffset(String name, Map<String, Integer> offsets) {
        return offsets.getOrDefault(name, 1);
    }

    private Map<String, Integer> stringOffsets(byte[] strings) {
        Map<String, Integer> offsets = new HashMap<>();
        int i = 1;
        while (i < strings.length) {
            int end = i;
            while (end < strings.length && strings[end] != 0) end++;
            offsets.put(new String(strings, i, end - i, StandardCharsets.UTF_8), i);
            i = end + 1;
        }
        return offsets;
    }

    private byte[] link(List<RawSection> sections) {
        Map<String, Integer> shName = stringOffsets(sectionStringTable().toByteArray());
        int headerSize = 64;
        long offset = headerSize;
        for (RawSection section : sections) {
            if (section.data().length > 0) {
                section.fileOffset = offset;
                offset += align(section.data().length, 8);
            }
        }
        long shOffset = offset;
        byte[] file = new byte[(int) (shOffset + sections.size() * 64L)];
        file[0] = 0x7f; file[1] = 'E'; file[2] = 'L'; file[3] = 'F';
        file[4] = 2; file[5] = 1; file[6] = 1;
        put16(file, 16, elfType);
        put16(file, 18, 62);
        put32(file, 20, 1);
        put64(file, 32, 0);
        put64(file, 40, shOffset);
        put16(file, 58, 64);
        put16(file, 60, sections.size());
        put16(file, 62, 2);
        for (RawSection section : sections) {
            System.arraycopy(section.data(), 0, file, (int) section.fileOffset, section.data().length);
            long base = shOffset + section.index() * 64L;
            put32(file, base, shName.getOrDefault(section.name(), 0));
            put32(file, base + 4, section.type());
        put64(file, base + 8, section.flags());
            put64(file, base + 16, 0);
            put64(file, base + 24, section.fileOffset);
            put64(file, base + 32, section.data().length);
            put32(file, base + 40, section.link());
            put32(file, base + 44, section.info());
            put64(file, base + 48, 1);
            put64(file, base + 56, section.entrySize());
        }
        return file;
    }

    private static long align(long value, long boundary) {
        return (value + boundary - 1) / boundary * boundary;
    }

    private static void write8(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
    }

    private static void write16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write(value >>> 8);
    }

    private static void write32(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 4; i++) out.write((int) (value >>> i * 8) & 0xff);
    }

    private static void write64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) out.write((int) (value >>> i * 8) & 0xff);
    }

    private static void put16(byte[] data, long offset, int value) {
        data[(int) offset] = (byte) value;
        data[(int) offset + 1] = (byte) (value >>> 8);
    }

    private static void put32(byte[] data, long offset, long value) {
        for (int i = 0; i < 4; i++) data[(int) offset + i] = (byte) (value >>> i * 8);
    }

    private static void put64(byte[] data, long offset, long value) {
        for (int i = 0; i < 8; i++) data[(int) offset + i] = (byte) (value >>> i * 8);
    }

    private static final class RawSection {
        private final int index;
        private final String name;
        private final int type;
        private final long flags;
        private final byte[] data;
        private final long link;
        private final long info;
        private final long entrySize;
        private long fileOffset;

        private RawSection(int index, String name, int type, long flags, byte[] data, long link,
                           long info, long entrySize) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.flags = flags;
            this.data = data;
            this.link = link;
            this.info = info;
            this.entrySize = entrySize;
        }

        int index() { return index; }
        String name() { return name; }
        int type() { return type; }
        long flags() { return flags; }
        byte[] data() { return data; }
        long link() { return link; }
        long info() { return info; }
        long entrySize() { return entrySize; }
    }
}
