package org.tribunal.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElfFixtureBuilder {
    public enum Binding { LOCAL(0), GLOBAL(1), WEAK(2); final int value; Binding(int value) { this.value = value; } }
    public enum Visibility { DEFAULT(0), INTERNAL(1), HIDDEN(2), PROTECTED(3); final int value; Visibility(int value) { this.value = value; } }
    public enum SymbolType { NOTYPE(0), OBJECT(1), FUNC(2), COMMON(5); final int value; SymbolType(int value) { this.value = value; } }

    private static final int SHT_PROGBITS=1,SHT_SYMTAB=2,SHT_STRTAB=3,SHT_RELA=4,SHT_DYNAMIC=6,SHT_NOBITS=8,SHT_DYNSYM=11,SHT_GROUP=17;
    private static final int SHF_ALLOC=2,SHF_GROUP=0x200,SHN_UNDEF=0,SHN_COMMON=0xfff2;
    private static final int ET_REL=1,ET_EXEC=2,ET_DYN=3,EM_X86_64=62,R_PLT32=4,R_COPY=5;
    private static final long BASE=0x4000;

    private final List<Symbol> symbols = new ArrayList<>();
    private final List<Relocation> relocations = new ArrayList<>();
    private final Map<String, Integer> sections = new LinkedHashMap<>();
    private final List<String> groups = new ArrayList<>();
    private String unitName = "fixture.o";
    private int type = ET_REL;
    private String needed, soname, verdef, verdefAlias, verneedFile, verneed;
    private boolean nonDefaultVersion;

    public ElfFixtureBuilder name(String value) { unitName = value; return this; }
    public ElfFixtureBuilder shared() { type = ET_DYN; return this; }
    public ElfFixtureBuilder executable() { type = ET_EXEC; return this; }
    public ElfFixtureBuilder needed(String value) { needed = value; return this; }
    public ElfFixtureBuilder soname(String value) { soname = value; return this; }
    public ElfFixtureBuilder verdef(String value) { verdef = value; return this; }
    public ElfFixtureBuilder verdefAlias(String value) { verdefAlias = value; return this; }
    public ElfFixtureBuilder nonDefaultVersion() { nonDefaultVersion = true; return this; }
    public ElfFixtureBuilder verneed(String file, String version) { verneedFile = file; verneed = version; return this; }
    public ElfFixtureBuilder text() { sections.put(".text", SHT_PROGBITS); return this; }
    public ElfFixtureBuilder bss() { sections.put(".bss", SHT_NOBITS); return this; }
    public ElfFixtureBuilder group(String signature) { groups.add(signature); sections.put(".text." + signature, SHT_PROGBITS); return this; }

    public ElfFixtureBuilder define(String name, String section, Binding binding, Visibility visibility,
                                    SymbolType symbolType, long value, long size) {
        symbols.add(new Symbol(name, section, binding, visibility, symbolType, value, size, 0)); return this;
    }
    public ElfFixtureBuilder defineVersioned(String name, String section, Binding binding, long value, int version) {
        symbols.add(new Symbol(name, section, binding, Visibility.DEFAULT, SymbolType.FUNC, value, 8, version)); return this;
    }
    public ElfFixtureBuilder common(String name, long size, Binding binding) {
        symbols.add(new Symbol(name, null, binding, Visibility.DEFAULT, SymbolType.COMMON, size, 0, 0)); return this;
    }
    public ElfFixtureBuilder reference(String name, Binding binding, int version) {
        symbols.add(new Symbol(name, null, binding, Visibility.DEFAULT, SymbolType.NOTYPE, 0, 0, version));
        relocations.add(new Relocation(name, 0x1000L + relocations.size() * 8, R_PLT32, -4)); return this;
    }
    public ElfFixtureBuilder copyReference(String name) {
        symbols.add(new Symbol(name, null, Binding.GLOBAL, Visibility.DEFAULT, SymbolType.OBJECT, 0, 8, 0));
        relocations.add(new Relocation(name, 0x2000, R_COPY, 0)); return this;
    }

    public byte[] build() {
        if (sections.isEmpty()) text();
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        Map<String, Integer> strOffsets = new LinkedHashMap<>();
        putString(blob, strOffsets, "");
        groups.forEach(group -> putString(blob, strOffsets, group));
        symbols.stream().map(symbol -> symbol.name).filter(name -> !name.isEmpty()).distinct()
                .forEach(name -> putString(blob, strOffsets, name));
        if (needed != null) putString(blob, strOffsets, needed);
        if (soname != null) putString(blob, strOffsets, soname);
        if (verneedFile != null) putString(blob, strOffsets, verneedFile);
        if (verdef != null) putString(blob, strOffsets, verdef);
        if (verdefAlias != null) putString(blob, strOffsets, verdefAlias);
        if (verneed != null) putString(blob, strOffsets, verneed);
        byte[] strtab = blob.toByteArray();

        List<SectionLayout> layouts = new ArrayList<>();
        layouts.add(new SectionLayout("", SHT_STRTAB, 0, 0, 0, new byte[0], 0));
        for (Map.Entry<String, Integer> entry : sections.entrySet()) {
            boolean grouped = entry.getKey().startsWith(".text.") && groups.contains(entry.getKey().substring(6));
            layouts.add(new SectionLayout(entry.getKey(), entry.getValue(), SHF_ALLOC | (grouped ? SHF_GROUP : 0),
                    0, grouped ? 2 + groups.indexOf(entry.getKey().substring(6)) : 0,
                    new byte[16], 0));
        }
        groups.forEach(signature -> {
            ByteArrayOutputStream groupData = new ByteArrayOutputStream();
            write32(groupData, 0); write32(groupData, 1);
            write32(groupData, indexOf(".text." + signature, layouts));
            layouts.add(new SectionLayout(".group." + signature, SHT_GROUP, 0, symtabIndex(sections.size() + groups.size() + 1),
                    2 + groups.indexOf(signature), groupData.toByteArray(), 0));
        });
        int current = layouts.size();
        int symtabIndex = current;
        int strtabIndex = current + 1;
        int shstrIndex = current + 2;
        int versymIndex = type != ET_REL ? current + 3 : -1;
        int verdefIndex = verdef != null ? current + 4 : -1;
        int verneedIndex = verneed != null ? (verdef != null ? current + 5 : current + 4) : -1;
        int dynamicIndex = type != ET_REL ? (verneed != null ? (verdef != null ? current + 6 : current + 5) : (verdef != null ? current + 5 : current + 4)) : -1;
        int relaIndex = relocations.isEmpty() ? -1 : layouts.size();
        byte[] symtabBytes = writeSymbols(layouts, strOffsets);
        byte[] shstr = buildShStringTable(layouts);
        byte[] versym = writeVersym();
        byte[] verdefBytes = writeVerdef(strOffsets);
        byte[] verneedBytes = writeVerneed(strOffsets);
        byte[] dynamicBytes = writeDynamic(strOffsets);
        byte[] rela = writeRelocations();
        layouts.add(new SectionLayout(type == ET_REL ? ".symtab" : ".dynsym", type == ET_REL ? SHT_SYMTAB : SHT_DYNSYM,
                0, strtabIndex, 2, symtabBytes, type == ET_REL ? 0 : BASE + sectionSlot(symtabIndex) * 512));
        layouts.add(new SectionLayout(type == ET_REL ? ".strtab" : ".dynstr", SHT_STRTAB, 0, 0, 0, strtab,
                type == ET_REL ? 0 : BASE + sectionSlot(strtabIndex) * 512));
        layouts.add(new SectionLayout(".shstrtab", SHT_STRTAB, 0, 0, 0, shstr, 0));
        if (versymIndex >= 0) layouts.add(new SectionLayout(".gnu.version", 0x6fffffff, 0, symtabIndex, 0, versym, BASE + sectionSlot(versymIndex) * 512));
        if (verdefIndex >= 0) layouts.add(new SectionLayout(".gnu.version_d", 0x6ffffffd, 0, strtabIndex, 0, verdefBytes, BASE + sectionSlot(verdefIndex) * 512));
        if (verneedIndex >= 0) layouts.add(new SectionLayout(".gnu.version_r", 0x6ffffffe, 0, strtabIndex, 0, verneedBytes, BASE + sectionSlot(verneedIndex) * 512));
        if (dynamicIndex >= 0) layouts.add(new SectionLayout(".dynamic", SHT_DYNAMIC, SHF_ALLOC | 1, strtabIndex, 0, dynamicBytes, BASE + sectionSlot(dynamicIndex) * 512));
        if (relaIndex >= 0) layouts.add(new SectionLayout(".rela.text", SHT_RELA, 0, symtabIndex, 1, rela, 0));
        return assemble(layouts, shstrIndex);
    }

    private int sectionSlot(int index) { return index - 1; }
    private int symtabIndex(int ignored) { return sections.size() + 1 + groups.size(); }
    private int indexOf(String name, List<SectionLayout> layouts) {
        for (int i=0;i<layouts.size();i++) if (layouts.get(i).name.equals(name)) return i;
        return 1;
    }
    private void putString(ByteArrayOutputStream out, Map<String, Integer> offsets, String value) {
        offsets.putIfAbsent(value, out.size());
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        out.write(0);
    }
    private byte[] writeSymbols(List<SectionLayout> layouts, Map<String, Integer> strOffsets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeSymbol(out, 0, 0, 0, 0, 0, 0);
        writeSymbol(out, 0, 3, 0, indexOf(sections.keySet().iterator().next(), layouts), 0, 0);
        groups.forEach(group -> writeSymbol(out, strOffsets.get(group), 0, 0, 1, 0, 0));
        for (Symbol symbol : symbols) {
            int info = symbol.binding.value << 4 | symbol.type.value;
            int shndx = symbol.type == SymbolType.COMMON ? SHN_COMMON
                    : symbol.section == null ? SHN_UNDEF : indexOf(symbol.section, layouts);
            writeSymbol(out, strOffsets.get(symbol.name), info, symbol.visibility.value, shndx,
                    symbol.value, symbol.size);
        }
        return out.toByteArray();
    }
    private void writeSymbol(ByteArrayOutputStream out, int name, int info, int other, int shndx, long value, long size) {
        write32(out, name); write8(out, info); write8(out, other); write16(out, shndx);
        write64(out, value); write64(out, size);
    }
    private byte[] writeVersym() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int total = 2 + groups.size() + symbols.size();
        for (int i=0;i<total;i++) {
            int raw = 1;
            if (i >= 2 + groups.size()) {
                Symbol symbol = symbols.get(i - 2 - groups.size());
                raw = symbol.rawVersion == 0 ? 1 : symbol.rawVersion;
                if (nonDefaultVersion && symbol.rawVersion == 2) raw |= 0x8000;
            }
            write16(out, raw);
        }
        return out.toByteArray();
    }
    private byte[] writeRelocations() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i=0;i<relocations.size();i++) {
            int symbolIndex = 2 + groups.size() + i;
            write64(out, relocations.get(i).offset);
            write64(out, ((long) symbolIndex << 32) | relocations.get(i).type);
            write64(out, relocations.get(i).addend);
        }
        return out.toByteArray();
    }
    private byte[] writeDynamic(Map<String, Integer> strings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (needed != null) tag(out, 1, strings.get(needed));
        if (soname != null) tag(out, 14, strings.get(soname));
        int firstExtra = sections.size() + 1 + groups.size();
        tag(out, 0x6ffffff0, BASE + (firstExtra + 3) * 512L);
        if (verdef != null) {
            tag(out, 0x6ffffffc, BASE + (firstExtra + 4) * 512L);
            tag(out, 0x6ffffffd, verdefAlias == null ? 1 : 2);
        }
        if (verneed != null) {
            tag(out, 0x6ffffffe, BASE + (firstExtra + (verdef != null ? 5 : 4)) * 512L);
            tag(out, 0x6fffffff, 1);
        }
        tag(out, 0, 0);
        return out.toByteArray();
    }
    private void tag(ByteArrayOutputStream out, long tag, long value) { write64(out, tag); write64(out, value); }
    private byte[] writeVerdef(Map<String, Integer> strings) {
        if (verdef == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = verdefAlias == null ? 1 : 2;
        write16(out, 1); write16(out, 0); write16(out, nonDefaultVersion ? 0x8002 : 2); write16(out, count);
        write32(out, 0); write32(out, count == 1 ? 0 : 20); write32(out, strings.get(verdef));
        write32(out, count == 1 ? 0 : 8);
        if (verdefAlias != null) { write32(out, strings.get(verdefAlias)); write32(out, 0); }
        return out.toByteArray();
    }
    private byte[] writeVerneed(Map<String, Integer> strings) {
        if (verneed == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, 1); write16(out, 1); write16(out, 0); write16(out, 1);
        write32(out, strings.get(verneedFile)); write32(out, 16); write32(out, 0);
        write16(out, 0); write16(out, 2); write32(out, strings.get(verneed)); write32(out, 0);
        return out.toByteArray();
    }
    private byte[] buildShStringTable(List<SectionLayout> layouts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        Map<String, Integer> offsets = new LinkedHashMap<>();
        layouts.stream().map(layout -> layout.name).filter(name -> !name.isEmpty()).forEach(name -> {
            if (!offsets.containsKey(name)) { offsets.put(name, out.size()); out.writeBytes(name.getBytes(StandardCharsets.UTF_8)); out.write(0); }
        });
        shOffsets = offsets;
        return out.toByteArray();
    }
    private Map<String, Integer> shOffsets = Map.of();
    private byte[] assemble(List<SectionLayout> layouts, int shstrIndex) {
        int sectionCount = layouts.size() + 1;
        long[] offsets = new long[sectionCount];
        long[] sizes = new long[sectionCount];
        long[] addresses = new long[sectionCount];
        long cursor = 64;
        for (int i=1;i<sectionCount;i++) {
            SectionLayout layout = layouts.get(i - 1);
            offsets[i] = cursor;
            sizes[i] = layout.type == SHT_NOBITS ? 16 : layout.data.length;
            addresses[i] = layout.address;
            cursor += sizes[i];
        }
        long shoff = cursor;
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[]{0x7f,'E','L','F',2,1,1,0,0,0,0,0,0,0,0,0});
        write16(file, type); write16(file, 62); write32(file, 1); write64(file, 0); write64(file, shoff);
        write32(file, 0); write16(file, 64); write16(file, sectionCount); write16(file, shstrIndex);
        write16(file, 0); write16(file, 0);
        for (int i=1;i<sectionCount;i++) file.writeBytes(layouts.get(i - 1).data);
        for (int i=0;i<sectionCount;i++) {
            SectionLayout layout = i == 0 ? null : layouts.get(i - 1);
            int typeValue = layout == null ? 0 : layout.type;
            long entrySize = typeValue == SHT_SYMTAB || typeValue == SHT_DYNSYM || typeValue == SHT_RELA ? 24 : 0;
            write32(file, layout == null || layout.name.isEmpty() ? 0 : shOffsets.getOrDefault(layout.name, 0));
            write32(file, typeValue);
            write64(file, layout == null ? 0 : layout.flags);
            write64(file, addresses[i]); write64(file, offsets[i]); write64(file, sizes[i]);
            write32(file, layout == null ? 0 : layout.link);
            write32(file, layout == null ? 0 : layout.info);
            write64(file, 1); write64(file, entrySize);
        }
        return file.toByteArray();
    }
    private static void write8(ByteArrayOutputStream out, int value) { out.write(value & 0xff); }
    private static void write16(ByteArrayOutputStream out, int value) { out.write(value & 0xff); out.write(value >>> 8 & 0xff); }
    private static void write32(ByteArrayOutputStream out, long value) { for (int i=0;i<4;i++) out.write((int)(value >>> i*8) & 0xff); }
    private static void write64(ByteArrayOutputStream out, long value) { for (int i=0;i<8;i++) out.write((int)(value >>> i*8) & 0xff); }
    private record Symbol(String name, String section, Binding binding, Visibility visibility, SymbolType type,
                          long value, long size, int rawVersion) {}
    private record Relocation(String name, long offset, int type, long addend) {}
    private record SectionLayout(String name, int type, long flags, int link, int info, byte[] data, long address) {}

    @SafeVarargs
    public static byte[] archive(java.util.Map.Entry<String, byte[]>... members) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        for (java.util.Map.Entry<String, byte[]> entry : members) {
            byte[] payload = entry.getValue();
            String prefix = entry.getKey() + "/";
            String header = prefix + " ".repeat(Math.max(1, 16 - prefix.length()))
                    + "0           0     100644  " + String.format("%-10d", payload.length) + "`\n";
            out.writeBytes(java.util.Arrays.copyOf(header.getBytes(StandardCharsets.US_ASCII), 60));
            out.writeBytes(payload);
            if ((payload.length & 1) == 1) out.write('\n');
        }
        return out.toByteArray();
    }
}
