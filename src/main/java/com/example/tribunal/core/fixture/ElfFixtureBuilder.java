package com.example.tribunal.core.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElfFixtureBuilder {
    public static final int ET_REL = 1, ET_EXEC = 2, ET_DYN = 3;
    public static final int STB_LOCAL = 0, STB_GLOBAL = 1, STB_WEAK = 2;
    public static final int STT_NOTYPE = 0, STT_OBJECT = 1, STT_FUNC = 2;
    public static final int STV_DEFAULT = 0, STV_HIDDEN = 2, STV_PROTECTED = 3;
    public static final int SHN_UNDEF = 0, SHN_ABS = 0xfff1, SHN_COMMON = 0xfff2;
    public static final int SHT_PROGBITS = 1, SHT_SYMTAB = 2, SHT_STRTAB = 3, SHT_RELA = 4, SHT_DYNAMIC = 6;
    public static final int SHT_DYNSYM = 11, SHT_GROUP = 17;
    public static final int R_X86_64_PC32 = 2, R_X86_64_COPY = 5, R_X86_64_RELATIVE = 8;
    private static final int SHF_ALLOC = 2, SHF_EXECINSTR = 4, SHF_WRITE = 1;

    public static class SymbolSpec {
        String name; int binding, type, visibility, section; long value, size;
        String version; boolean versionNeed;
        SymbolSpec(String name, int binding, int type, int visibility, int section, long value, long size,
                   String version, boolean versionNeed) {
            this.name=name; this.binding=binding; this.type=type; this.visibility=visibility; this.section=section;
            this.value=value; this.size=size; this.version=version; this.versionNeed=versionNeed;
        }
    }
    public record RelSpec(String targetSection, int symbol, int type, long offset, long addend) {}
    public record GroupSpec(int signatureSymbol, List<Integer> members, boolean comdat) {}
    public record VersionDef(String primary, List<String> aliases, int index, boolean nonDefault) {}
    public record VersionNeed(String file, String name, int index) {}
    public record SectionMeta(String name, int type, long flags, int link, int info, long alignment,
                              long entrySize, byte[] data) {}

    private final String name;
    private final int type;
    private final List<SymbolSpec> symbols = new ArrayList<>();
    private final List<RelSpec> relocations = new ArrayList<>();
    private final List<GroupSpec> groups = new ArrayList<>();
    private final List<VersionDef> versionDefs = new ArrayList<>();
    private final List<VersionNeed> versionNeeds = new ArrayList<>();
    private final Map<String, byte[]> sectionData = new LinkedHashMap<>();
    private final Map<String, Integer> sectionTypes = new LinkedHashMap<>();
    private final Map<String, Long> sectionFlags = new LinkedHashMap<>();
    private final Map<String, Integer> sectionLinks = new LinkedHashMap<>();
    private final Map<String, Integer> sectionInfo = new LinkedHashMap<>();
    private final Map<String, Long> sectionEntrySize = new LinkedHashMap<>();
    private String needed;
    private String soname;

    public ElfFixtureBuilder(String name, int type) {
        this.name = name;
        this.type = type;
        symbols.add(new SymbolSpec("", STB_LOCAL, STT_NOTYPE, STV_DEFAULT, 0, 0, 0, null, false));
        section(".text", new byte[8], SHT_PROGBITS, (long) SHF_ALLOC | SHF_EXECINSTR);
        if (type != ET_REL) section(".data", new byte[16], SHT_PROGBITS, (long) SHF_ALLOC | SHF_WRITE);
    }

    public static ElfFixtureBuilder relocatable(String name) { return new ElfFixtureBuilder(name, ET_REL); }
    public static ElfFixtureBuilder shared(String name) { return new ElfFixtureBuilder(name, ET_DYN); }
    public static ElfFixtureBuilder executable(String name) { return new ElfFixtureBuilder(name, ET_EXEC); }

    public int symbol(String name, int binding, int type, int visibility, int section, long value, long size) {
        symbols.add(new SymbolSpec(name, binding, type, visibility, section, value, size, null, false));
        return symbols.size() - 1;
    }

    public int undefined(String name, int binding) {
        return symbol(name, binding, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF, 0, 0);
    }

    public int defined(String name, int binding, int type, int visibility, int section, long value, long size) {
        return symbol(name, binding, type, visibility, section, value, size);
    }

    public int common(String name, long size, int binding) {
        return symbol(name, binding, STT_OBJECT, STV_DEFAULT, SHN_COMMON, 16, size);
    }

    public int versionedDefined(String name, String version, int type, int visibility, long value, long size) {
        int index = symbol(name, STB_GLOBAL, type, visibility, 2, value, size);
        symbols.get(index).version = version;
        return index;
    }

    public int versionedUndefined(String name, String file, String version) {
        int index = undefined(name, STB_GLOBAL);
        symbols.get(index).version = version;
        symbols.get(index).versionNeed = true;
        versionNeeds.add(new VersionNeed(file, version, index + 2));
        return index;
    }

    public ElfFixtureBuilder defineVersion(String primary, List<String> aliases, int index, boolean nonDefault) {
        versionDefs.add(new VersionDef(primary, aliases, index, nonDefault));
        return this;
    }

    public ElfFixtureBuilder soname(String value) { this.soname = value; return this; }
    public ElfFixtureBuilder needed(String value) { this.needed = value; return this; }

    public ElfFixtureBuilder relocation(String targetSection, int symbolIndex, int type, long offset, long addend) {
        relocations.add(new RelSpec(targetSection, symbolIndex, type, offset, addend));
        return this;
    }

    public ElfFixtureBuilder group(int signatureSymbol, List<Integer> members, boolean comdat) {
        groups.add(new GroupSpec(signatureSymbol, members, comdat));
        return this;
    }

    public ElfFixtureBuilder section(String name, byte[] bytes, int type, long flags) {
        sectionData.put(name, bytes);
        sectionTypes.put(name, type);
        sectionFlags.put(name, flags);
        return this;
    }

    public byte[] build() {
        int localCount = (int) symbols.stream().takeWhile(s -> s.binding == STB_LOCAL).count();
        Map<String, Integer> symbolVersionIndex = new LinkedHashMap<>();
        if (type != ET_REL && (!versionDefs.isEmpty() || !versionNeeds.isEmpty())) {
            int next = 2;
            for (VersionDef definition : versionDefs) {
                int index = definition.index() > 0 ? definition.index() : next++;
                for (SymbolSpec symbol : symbols) if (symbol.version != null && definition.aliases.contains(symbol.version)) symbolVersionIndex.put(symbol.name, index);
            }
            for (VersionNeed need : versionNeeds) {
                for (SymbolSpec symbol : symbols) if (symbol.versionNeed && symbol.version.equals(need.name())) symbolVersionIndex.put(symbol.name, need.index());
            }
        }
        byte[] symbolNames = buildSymbolNames();
        byte[] dynamicStrings = buildDynamicStrings(symbolNames);
        byte[] symbolTable = buildSymbols(symbolVersionIndex);
        byte[] rela = buildRela();
        byte[] groupBytes = buildGroups(localCount);
        byte[] version = buildVersionSymbols(symbolVersionIndex);
        int baseSectionSize = sectionData.values().stream().mapToInt(v -> v.length).sum();
        byte[] provisionalSectionNames = strings(buildSectionNameTable());
        int dynamicOffset = 64 + baseSectionSize + provisionalSectionNames.length
                + dynamicStrings.length + symbolTable.length;
        int estimatedDefSize = versionDefs.stream().mapToInt(d -> 20 + d.aliases.size() * 16).sum();
        int dynamicSize = estimateDynamicSize(dynamicStrings);
        int versionDefOffset = dynamicOffset + dynamicSize + version.length;
        int versionNeedOffset = versionDefOffset + estimatedDefSize;
        byte[] dynamic = buildDynamic(dynamicStrings, versionDefOffset, versionNeedOffset);
        byte[] versionDef = buildVersionDefs(dynamicStrings);
        byte[] versionNeed = buildVersionNeeds(dynamicStrings);
        byte[] sectionNames = strings(buildSectionNameTable());

        List<SectionMeta> metas = new ArrayList<>();
        int dataIndex = sectionData.containsKey(".data") ? 2 : -1;
        for (Map.Entry<String, byte[]> e : sectionData.entrySet()) {
            metas.add(new SectionMeta(e.getKey(), sectionTypes.get(e.getKey()), sectionFlags.get(e.getKey()), 0, 0, 1, 0, e.getValue()));
        }
        int shstrIndex = metas.size() + 1;
        int symIndex;
        if (type == ET_REL) {
            int strIndex = metas.size() + 2;
            symIndex = metas.size() + 3;
            metas.add(new SectionMeta(".shstrtab", SHT_STRTAB, 0, 0, 0, 1, 0, sectionNames));
            metas.add(new SectionMeta(".strtab", SHT_STRTAB, 0, 0, 0, 1, 0, symbolNames));
            metas.add(new SectionMeta(".symtab", SHT_SYMTAB, 0, strIndex, localCount, 8, 24, symbolTable));
            if (groupBytes.length > 0) metas.add(new SectionMeta(".group", SHT_GROUP, 0, symIndex, groups.get(0).signatureSymbol(), 4, 4, groupBytes));
            if (rela.length > 0) metas.add(new SectionMeta(".rela.text", SHT_RELA, 0, symIndex, 1, 8, 24, rela));
        } else {
            int dynstrIndex = metas.size() + 2;
            int dynsymIndex = metas.size() + 3;
            metas.add(new SectionMeta(".shstrtab", SHT_STRTAB, 0, 0, 0, 1, 0, sectionNames));
            metas.add(new SectionMeta(".dynstr", SHT_STRTAB, 0, 0, 0, 1, 0, dynamicStrings));
            metas.add(new SectionMeta(".dynsym", SHT_DYNSYM, 2, dynstrIndex, localCount, 8, 24, symbolTable));
            int dynamicIndex = metas.size() + 1;
            metas.add(new SectionMeta(".dynamic", SHT_DYNAMIC, SHF_WRITE | SHF_ALLOC, dynstrIndex, 0, 8, 16, dynamic));
            int versionIndex = metas.size() + 1;
            metas.add(new SectionMeta(".gnu.version", 0x6fffffff, 2, dynsymIndex, 0, 2, 2, version));
            int vdIndex = metas.size() + 1;
            metas.add(new SectionMeta(".gnu.version_d", 0x6ffffffd, 0, dynstrIndex, 0, 4, 0, versionDef));
            int vnIndex = metas.size() + 1;
            metas.add(new SectionMeta(".gnu.version_r", 0x6ffffffe, 0, dynstrIndex, 0, 4, 0, versionNeed));
            if (rela.length > 0) metas.add(new SectionMeta(".rela.dyn", SHT_RELA, SHF_ALLOC | SHF_WRITE, dynsymIndex, 0, 8, 24, rela));
        }
        return assemble(metas);
    }


    private String buildSectionNameTable() {
        List<String> names = new ArrayList<>();
        names.add("");
        names.addAll(sectionData.keySet());
        names.add(".shstrtab");
        if (type == ET_REL) { names.add(".strtab"); names.add(".symtab"); names.add(".rela.text"); names.add(".group"); }
        else { names.add(".dynstr"); names.add(".dynsym"); names.add(".dynamic"); names.add(".gnu.version"); names.add(".gnu.version_d"); names.add(".gnu.version_r"); names.add(".rela.dyn"); }
        StringBuilder builder = new StringBuilder();
        for (String name : names) builder.append(name).append('\0');
        return builder.toString();
    }

    private int sectionNameOffset(String table, String name) { return stringOffset(table.getBytes(StandardCharsets.UTF_8), name); }

    private byte[] strings(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    private byte[] buildSymbolNames() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        for (int i = 1; i < symbols.size(); i++) {
            try { out.write(symbols.get(i).name.getBytes(StandardCharsets.UTF_8)); out.write(0); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        return out.toByteArray();
    }

    private byte[] buildDynamicStrings(byte[] symbolNames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        List<String> values = new ArrayList<>();
        if (soname != null && !soname.isBlank()) values.add(soname);
        if (needed != null && !needed.isBlank()) values.add(needed);
        for (int i = 1; i < symbols.size(); i++) values.add(symbols.get(i).name);
        for (VersionDef d : versionDefs) values.addAll(d.aliases);
        for (VersionNeed n : versionNeeds) { values.add(n.file); values.add(n.name); }
        for (String value : values.stream().distinct().toList()) {
            try { out.write(value.getBytes(StandardCharsets.UTF_8)); out.write(0); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        return out.toByteArray();
    }

    private int stringOffset(byte[] table, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= table.length - bytes.length; i++) {
            for (int j = 0; j < bytes.length; j++) if (table[i + j] != bytes[j]) continue outer;
            return i;
        }
        throw new IllegalStateException("missing string " + value);
    }

    private byte[] buildSymbols(Map<String, Integer> versionIndices) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int nameOffset = 1;
        byte[] names = type == ET_REL ? buildSymbolNames() : buildDynamicStrings(buildSymbolNames());
        for (int i = 0; i < symbols.size(); i++) {
            SymbolSpec s = symbols.get(i);
            int name = i == 0 ? 0 : stringOffset(names, s.name);
            if (i > 0) nameOffset += s.name.getBytes(StandardCharsets.UTF_8).length + 1;
            int section = s.section;
            long value = s.value;
            if (s.section == SHN_COMMON) value = s.size;
            writeWord(out, name);
            out.write((s.type & 15) | ((s.binding & 15) << 4));
            out.write(s.visibility & 7);
            writeHalf(out, section);
            writeLong(out, value);
            writeLong(out, s.size);
        }
        return out.toByteArray();
    }

    private byte[] buildRela() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (RelSpec rel : relocations) {
            writeLong(out, rel.offset);
            writeLong(out, ((long) rel.symbol << 32) | (rel.type & 0xffffffffL));
            writeLong(out, rel.addend);
        }
        return out.toByteArray();
    }

    private byte[] buildGroups(int localCount) {
        if (groups.isEmpty()) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeWord(out, groups.get(0).comdat ? 1 : 0);
        for (Integer member : groups.get(0).members) writeWord(out, member);
        return out.toByteArray();
    }

    private byte[] buildDynamic(byte[] strings, int verdefOffset, int verneedOffset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (needed != null && !needed.isBlank()) {
            writeLong(out, 1); writeLong(out, stringOffset(strings, needed));
        }
        if (soname != null && !soname.isBlank()) {
            writeLong(out, 14); writeLong(out, stringOffset(strings, soname));
        }
        if (!versionDefs.isEmpty()) {
            writeLong(out, 0x6ffffffcL); writeLong(out, verdefOffset);
            writeLong(out, 0x6ffffffdL); writeLong(out, versionDefs.size());
        }
        if (!versionNeeds.isEmpty()) {
            writeLong(out, 0x6ffffffeL); writeLong(out, verneedOffset);
            writeLong(out, 0x6fffffffL); writeLong(out, versionNeeds.stream().map(VersionNeed::file).distinct().count());
        }
        writeLong(out, 0); writeLong(out, 0);
        return out.toByteArray();
    }


    private int estimateDynamicSize(byte[] strings) {
        int entries = 1;
        if (needed != null && !needed.isBlank()) entries++;
        if (soname != null && !soname.isBlank()) entries++;
        if (!versionDefs.isEmpty()) entries += 2;
        if (!versionNeeds.isEmpty()) entries += 2;
        return entries * 16;
    }

    private byte[] buildVersionSymbols(Map<String, Integer> indices) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < symbols.size(); i++) {
            SymbolSpec symbol = symbols.get(i);
            int index = i == 0 ? 0 : indices.getOrDefault(symbol.name, 1);
            writeHalf(out, index);
        }
        return out.toByteArray();
    }

    private byte[] buildVersionDefs(byte[] strings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (VersionDef definition : versionDefs) {
            int defStart = out.size();
            int auxCount = definition.aliases.size();
            int defSize = 20 + auxCount * 16;
            writeHalf(out, 1);
            writeHalf(out, definition.nonDefault ? 2 : 0);
            writeHalf(out, definition.index());
            writeHalf(out, auxCount);
            writeWord(out, 20);
            writeWord(out, 0);
            writeWord(out, defSize);
            for (int i = 0; i < auxCount; i++) {
                writeWord(out, 0);
                writeWord(out, stringOffset(strings, definition.aliases.get(i)));
                writeWord(out, i + 1 < auxCount ? 16 : 0);
                writeWord(out, 0);
            }
            byte[] all = out.toByteArray();
            // There is one definition in these fixtures; patch next=0 at offset 16.
            writeAtWord(all, defStart + 16, 0);
            out.reset();
            out.writeBytes(all);
        }
        return out.toByteArray();
    }

    private byte[] buildVersionNeeds(byte[] strings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, List<VersionNeed>> byFile = new LinkedHashMap<>();
        for (VersionNeed need : versionNeeds) byFile.computeIfAbsent(need.file, ignored -> new ArrayList<>()).add(need);
        for (var entry : byFile.entrySet()) {
            int start = out.size();
            int auxCount = entry.getValue().size();
            int total = 16 + auxCount * 16;
            writeHalf(out, 1);
            writeHalf(out, auxCount);
            writeWord(out, stringOffset(strings, entry.getKey()));
            writeWord(out, 16);
            writeWord(out, 0);
            for (int i = 0; i < auxCount; i++) {
                VersionNeed need = entry.getValue().get(i);
                writeWord(out, 0);
                writeHalf(out, 0);
                writeHalf(out, need.index());
                writeWord(out, stringOffset(strings, need.name()));
                writeWord(out, i + 1 < auxCount ? 16 : 0);
            }
            byte[] all = out.toByteArray();
            out.reset();
            out.writeBytes(all);
        }
        return out.toByteArray();
    }

    private byte[] assemble(List<SectionMeta> metas) {
        int headerSize = 64;
        int offset = headerSize;
        List<Integer> offsets = new ArrayList<>();
        List<byte[]> bytes = new ArrayList<>();
        for (SectionMeta meta : metas) {
            offsets.add(offset);
            bytes.add(meta.data);
            offset += meta.data.length;
        }
        int sectionOffset = align(offset, 8);
        ByteBuffer buffer = ByteBuffer.allocate(sectionOffset + (metas.size() + 1) * 64).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{0x7f,'E','L','F',2,1,1,0,0,0,0,0,0,0,0,0});
        buffer.putShort(16, (short) type);
        buffer.putShort(18, (short) 62);
        buffer.putInt(20, 1);
        buffer.putLong(24, 0);
        buffer.putLong(32, 0);
        buffer.putLong(40, sectionOffset);
        buffer.putInt(48, 0);
        buffer.putShort(52, (short) 64);
        buffer.putShort(54, (short) 0);
        buffer.putShort(56, (short) 0);
        buffer.putShort(58, (short) 64);
        buffer.putShort(60, (short) (metas.size() + 1));
        buffer.putShort(62, (short) (metas.stream().filter(m -> m.name.equals(".shstrtab")).findFirst().map(metas::indexOf).orElse(0) + 1));
        buffer.position(64);
        for (int i = 0; i < metas.size(); i++) buffer.put(bytes.get(i));
        while (buffer.position() < sectionOffset) buffer.put((byte) 0);
        writeSectionHeader(buffer, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        for (int i = 0; i < metas.size(); i++) {
            SectionMeta m = metas.get(i);
            writeSectionHeader(buffer, sectionNameOffset(buildSectionNameTable(), m.name), m.type, m.flags, 0, m.data.length > 0 ? offsets.get(i) : 0,
                    m.data.length, m.link, m.info, m.alignment, m.entrySize);
        }
        return buffer.array();
    }

    private int nameIndex(String name) {
        return switch (name) {
            case ".null" -> 0;
            case ".text" -> 1;
            case ".data" -> 7;
            case ".shstrtab" -> 13;
            case ".strtab" -> 23;
            case ".symtab" -> 31;
            case ".rela.text" -> 39;
            case ".group" -> 50;
            case ".dynsym" -> 57;
            case ".dynstr" -> 65;
            case ".dynamic" -> 73;
            case ".gnu.version" -> 82;
            case ".gnu.version_d" -> 95;
            case ".gnu.version_r" -> 110;
            case ".rela.dyn" -> 125;
            default -> throw new IllegalStateException(name);
        };
    }

    private void writeSectionHeader(ByteBuffer b, int name, int type, long flags, long address, long offset,
                                    long size, int link, int info, long alignment, long entrySize) {
        b.putInt(name); b.putInt(type); b.putLong(flags); b.putLong(address); b.putLong(offset); b.putLong(size);
        b.putInt(link);
        b.putInt(info);
        b.putLong(alignment);
        b.putLong(entrySize);
    }

    private int align(int value, int alignment) { return (value + alignment - 1) / alignment * alignment; }
    private void writeHalf(ByteArrayOutputStream out, int value) { out.write(value & 255); out.write((value >>> 8) & 255); }
    private void writeWord(ByteArrayOutputStream out, long value) { for (int i=0;i<4;i++) out.write((int)(value >>> (i*8)) & 255); }
    private void writeLong(ByteArrayOutputStream out, long value) { for (int i=0;i<8;i++) out.write((int)(value >>> (i*8)) & 255); }
    private void writeAtWord(byte[] data, int offset, int value) { data[offset]=(byte)value; data[offset+1]=(byte)(value>>>8); data[offset+2]=(byte)(value>>>2); data[offset+3]=(byte)(value>>>24); }

    public String name() { return name; }
}
