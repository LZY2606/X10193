package com.symbolcourt.fixture;

import com.symbolcourt.elf.Constants;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElfFixtureBuilder {

    public enum Bind { LOCAL, GLOBAL, WEAK }
    public enum SymType { NOTYPE, OBJECT, FUNC, SECTION }

    private boolean elf64 = true;
    private int machine = 62;
    private int type = Constants.ET_REL;
    private final List<Sect> sections = new ArrayList<>();
    private final List<SymDef> symbols = new ArrayList<>();
    private final List<RelDef> relocations = new ArrayList<>();
    private final List<Version> versions = new ArrayList<>();
    private final List<String> needed = new ArrayList<>();
    private String soname;

    public ElfFixtureBuilder elf64(boolean value) {
        this.elf64 = value;
        return this;
    }

    public ElfFixtureBuilder machine(int value) {
        this.machine = value;
        return this;
    }

    public ElfFixtureBuilder relocatable() {
        this.type = Constants.ET_REL;
        return this;
    }

    public ElfFixtureBuilder shared() {
        this.type = Constants.ET_DYN;
        return this;
    }

    public ElfFixtureBuilder executable() {
        this.type = Constants.ET_EXEC;
        return this;
    }

    public ElfFixtureBuilder needed(String library) {
        needed.add(library);
        return this;
    }

    public ElfFixtureBuilder soname(String name) {
        this.soname = name;
        return this;
    }

    public SectBuilder section(String name) {
        return new SectBuilder(name);
    }

    public SymBuilder symbol(String name) {
        return new SymBuilder(name);
    }

    public RelBuilder reloc(String symbolName) {
        return new RelBuilder(symbolName);
    }

    public VersionBuilder version(String name) {
        return new VersionBuilder(name);
    }

    public class SectBuilder {
        private final String name;
        private byte[] data = new byte[0];
        private int sectType = Constants.SHT_PROGBITS;
        private boolean alloc = true;
        private String comdatKey;
        private String[] comdatMembers;
        private long address;
        private long entsize;
        private long flags;

        SectBuilder(String name) {
            this.name = name;
        }

        public SectBuilder data(byte[] bytes) {
            this.data = bytes;
            return this;
        }

        public SectBuilder size(int bytes) {
            this.data = new byte[bytes];
            return this;
        }

        public SectBuilder noalloc() {
            this.alloc = false;
            return this;
        }

        public SectBuilder type(int value) {
            this.sectType = value;
            return this;
        }

        public SectBuilder address(long value) {
            this.address = value;
            return this;
        }

        public SectBuilder entsize(long value) {
            this.entsize = value;
            return this;
        }

        public SectBuilder comdat(String key, String... members) {
            this.comdatKey = key;
            this.comdatMembers = members;
            this.flags |= Constants.SHF_GROUP;
            return this;
        }

        public ElfFixtureBuilder add() {
            Sect section = new Sect();
            section.name = name;
            section.data = data;
            section.type = sectType;
            section.flags = flags | (alloc ? Constants.SHF_ALLOC : 0);
            section.comdatKey = comdatKey;
            section.comdatMembers = comdatMembers;
            section.address = address;
            section.entsize = entsize;
            sections.add(section);
            return ElfFixtureBuilder.this;
        }
    }

    public class SymBuilder {
        private final String name;
        private Bind bind = Bind.GLOBAL;
        private SymType symType = SymType.NOTYPE;
        private int visibility = Constants.STV_DEFAULT;
        private String section;
        private boolean undefined;
        private boolean common;
        private long value;
        private long size;
        private String version;
        private boolean nonDefaultVersion;

        SymBuilder(String name) {
            this.name = name;
        }

        public SymBuilder bind(Bind value) {
            this.bind = value;
            return this;
        }

        public SymBuilder weak() {
            this.bind = Bind.WEAK;
            return this;
        }

        public SymBuilder local() {
            this.bind = Bind.LOCAL;
            return this;
        }

        public SymBuilder type(SymType value) {
            this.symType = value;
            return this;
        }

        public SymBuilder visibility(int value) {
            this.visibility = value;
            return this;
        }

        public SymBuilder hidden() {
            this.visibility = Constants.STV_HIDDEN;
            return this;
        }

        public SymBuilder protectedVisibility() {
            this.visibility = Constants.STV_PROTECTED;
            return this;
        }

        public SymBuilder in(String sectionName) {
            this.section = sectionName;
            return this;
        }

        public SymBuilder undefined() {
            this.undefined = true;
            return this;
        }

        public SymBuilder common(long alignment) {
            this.common = true;
            this.value = alignment;
            return this;
        }

        public SymBuilder value(long value) {
            this.value = value;
            return this;
        }

        public SymBuilder size(long size) {
            this.size = size;
            return this;
        }

        public SymBuilder version(String versionName) {
            this.version = versionName;
            return this;
        }

        public SymBuilder nonDefaultVersion() {
            this.nonDefaultVersion = true;
            return this;
        }

        public ElfFixtureBuilder add() {
            SymDef def = new SymDef();
            def.name = name;
            def.bind = bind;
            def.type = symType;
            def.visibility = visibility;
            def.section = section;
            def.undefined = undefined;
            def.common = common;
            def.value = value;
            def.size = size;
            def.version = version;
            def.nonDefault = nonDefaultVersion;
            symbols.add(def);
            return ElfFixtureBuilder.this;
        }
    }

    public class RelBuilder {
        private final String symbol;
        private String section;
        private long offset;
        private long type;
        private Long addend;

        RelBuilder(String symbol) {
            this.symbol = symbol;
        }

        public RelBuilder in(String sectionName) {
            this.section = sectionName;
            return this;
        }

        public RelBuilder offset(long value) {
            this.offset = value;
            return this;
        }

        public RelBuilder type(long value) {
            this.type = value;
            return this;
        }

        public RelBuilder addend(long value) {
            this.addend = value;
            return this;
        }

        public ElfFixtureBuilder add() {
            RelDef def = new RelDef();
            def.symbol = symbol;
            def.section = section;
            def.offset = offset;
            def.type = type;
            def.addend = addend;
            relocations.add(def);
            return this;
        }
    }

    public class VersionBuilder {
        private final String name;
        private String parent;
        private boolean need;
        private String fromFile = "libneed.so";

        VersionBuilder(String name) {
            this.name = name;
        }

        public VersionBuilder parent(String parentName) {
            this.parent = parentName;
            return this;
        }

        public VersionBuilder need(String fileName) {
            this.need = true;
            this.fromFile = fileName;
            return this;
        }

        public ElfFixtureBuilder add() {
            Version version = new Version();
            version.name = name;
            version.parent = parent;
            version.need = need;
            version.fromFile = fromFile;
            versions.add(version);
            return this;
        }
    }

    private static class Sect {
        String name;
        byte[] data;
        int type;
        long flags;
        long address;
        long entsize;
        int link;
        int info;
        String comdatKey;
        String[] comdatMembers;
        boolean isGroup;
        long fileOffset;
        long runtimeAddress;
        List<RelDef> relDefs;
        Sect linkPlaceholder;
        String infoPlaceholderName;
    }

    private static class SymDef {
        String name;
        Bind bind;
        SymType type;
        int visibility;
        String section;
        boolean undefined;
        boolean common;
        long value;
        long size;
        String version;
        boolean nonDefault;
    }

    private static class RelDef {
        String symbol;
        String section;
        long offset;
        long type;
        Long addend;
    }

    private static class Version {
        String name;
        String parent;
        boolean need;
        String fromFile;
    }

    private static class GroupLayout {
        Sect group;
        List<String> members;
    }

    public byte[] build() {
        Map<String, Integer> versionIndex = new LinkedHashMap<>();
        int nextVersionIndex = 2;
        for (Version version : versions) {
            versionIndex.put(version.name, nextVersionIndex++);
        }

        ByteArrayOutputStream sectionNames = new ByteArrayOutputStream();
        sectionNames.write(0);
        Map<String, Integer> sectionNameOffsets = new LinkedHashMap<>();
        for (Sect section : sections) {
            putString(sectionNames, section.name, sectionNameOffsets);
        }

        boolean dynamicMode = type != Constants.ET_REL;
        boolean versioned = !versions.isEmpty();
        ByteArrayOutputStream dynstr = new ByteArrayOutputStream();
        dynstr.write(0);
        Map<String, Integer> dynstrOffsets = new LinkedHashMap<>();
        if (dynamicMode || versioned) {
            for (SymDef symbol : symbols) {
                putString(dynstr, symbol.name, dynstrOffsets);
            }
            for (String library : needed) {
                putString(dynstr, library, dynstrOffsets);
            }
            if (soname != null) {
                putString(dynstr, soname, dynstrOffsets);
            }
            for (Version version : versions) {
                putString(dynstr, version.name, dynstrOffsets);
                if (version.parent != null) {
                    putString(dynstr, version.parent, dynstrOffsets);
                }
                if (version.need) {
                    putString(dynstr, version.fromFile, dynstrOffsets);
                }
            }
        }

        List<SymDef> locals = new ArrayList<>();
        List<SymDef> globals = new ArrayList<>();
        for (SymDef symbol : symbols) {
            (symbol.bind == Bind.LOCAL ? locals : globals).add(symbol);
        }
        List<SymDef> orderedSymbols = new ArrayList<>(locals);
        orderedSymbols.addAll(globals);
        int localCount = locals.size() + 1;

        ByteArrayOutputStream symStringTable = new ByteArrayOutputStream();
        symStringTable.write(0);
        Map<String, Integer> symbolStringOffsets = new LinkedHashMap<>();
        for (SymDef symbol : orderedSymbols) {
            putString(symStringTable, symbol.name, symbolStringOffsets);
        }

        List<GroupLayout> groups = buildGroups(sections);

        Sect shstrtab = new Sect();
        shstrtab.name = ".shstrtab";
        shstrtab.type = Constants.SHT_STRTAB;
        shstrtab.data = sectionNames.toByteArray();

        Sect strtab = new Sect();
        strtab.name = ".strtab";
        strtab.type = Constants.SHT_STRTAB;
        strtab.data = symStringTable.toByteArray();

        Sect dynsym = null;
        Sect dynstrSection = null;
        if (dynamicMode || versioned) {
            dynstrSection = new Sect();
            dynstrSection.name = ".dynstr";
            dynstrSection.type = Constants.SHT_STRTAB;
            dynstrSection.flags = Constants.SHF_ALLOC;
            dynstrSection.data = dynstr.toByteArray();

            dynsym = new Sect();
            dynsym.name = ".dynsym";
            dynsym.type = Constants.SHT_DYNSYM;
            dynsym.flags = Constants.SHF_ALLOC;
            dynsym.entsize = elf64 ? 24 : 16;
            dynsym.linkPlaceholder = dynstrSection;
            dynsym.info = localCount;
        }

        Sect staticSymtab = null;
        if (type == Constants.ET_REL) {
            staticSymtab = new Sect();
            staticSymtab.name = ".symtab";
            staticSymtab.type = Constants.SHT_SYMTAB;
            staticSymtab.entsize = elf64 ? 24 : 16;
            staticSymtab.linkPlaceholder = strtab;
            staticSymtab.info = localCount;
        }

        Sect versym = null;
        Sect verdef = null;
        Sect verneed = null;
        if (versioned) {
            versym = new Sect();
            versym.name = ".gnu.version";
            versym.type = 0x6fffffff;
            versym.flags = Constants.SHF_ALLOC;
            versym.entsize = 2;
            versym.linkPlaceholder = dynsym;
            versym.data = buildVersym(orderedSymbols, versionIndex);

            verdef = new Sect();
            verdef.name = ".gnu.version_d";
            verdef.type = 0x6ffffffe;
            verdef.flags = Constants.SHF_ALLOC;
            verdef.linkPlaceholder = dynstrSection;
            verdef.info = versions.stream().filter(v -> !v.need).mapToInt(v -> versionIndex.get(v.name)).min().orElse(0);
            verdef.data = buildVerdef(versions, dynstrOffsets, false);

            verneed = new Sect();
            verneed.name = ".gnu.version_r";
            verneed.type = 0x6fffffff;
            verneed.flags = Constants.SHF_ALLOC;
            verneed.linkPlaceholder = dynstrSection;
            verneed.data = buildVerneed(versions, dynstrOffsets);
        }

        Sect dynamic = null;
        if (dynamicMode) {
            dynamic = new Sect();
            dynamic.name = ".dynamic";
            dynamic.type = Constants.SHT_DYNAMIC;
            dynamic.flags = Constants.SHF_ALLOC;
            dynamic.entsize = elf64 ? 16 : 8;
            dynamic.linkPlaceholder = dynstrSection;
            dynamic.data = buildDynamic(dynstrOffsets, verdef != null);
        }

        Map<String, Sect> relocSections = new LinkedHashMap<>();
        Map<String, List<RelDef>> byTarget = new LinkedHashMap<>();
        for (RelDef relocation : relocations) {
            byTarget.computeIfAbsent(relocation.section, k -> new ArrayList<>()).add(relocation);
        }
        for (Map.Entry<String, List<RelDef>> entry : byTarget.entrySet()) {
            Sect section = new Sect();
            boolean rela = elf64 || entry.getValue().stream().anyMatch(rel -> rel.addend != null);
            section.name = (rela ? ".rela" : ".rel") + entry.getKey();
            section.type = rela ? Constants.SHT_RELA : Constants.SHT_REL;
            section.entsize = rela ? (elf64 ? 24 : 12) : (elf64 ? 16 : 8);
            section.flags = dynamicMode ? Constants.SHF_ALLOC : 0;
            section.linkPlaceholder = type == Constants.ET_REL ? staticSymtab : dynsym;
            section.infoPlaceholderName = entry.getKey();
            section.relDefs = entry.getValue();
            relocSections.put(entry.getKey(), section);
        }

        List<Sect> layout = new ArrayList<>();
        for (GroupLayout group : groups) {
            layout.add(group.group);
            for (String member : group.members) {
                layout.add(findSection(sections, member));
            }
        }
        for (Sect section : sections) {
            if (!inAnyGroup(section.name, groups)) {
                layout.add(section);
            }
        }
        for (Sect section : relocSections.values()) {
            layout.add(section);
        }
        if (dynamic != null) {
            layout.add(dynamic);
        }
        if (dynsym != null) {
            layout.add(dynsym);
        }
        if (dynstrSection != null) {
            layout.add(dynstrSection);
        }
        if (versym != null) {
            layout.add(versym);
        }
        if (verdef != null && verdef.data.length > 0) {
            layout.add(verdef);
        }
        if (verneed != null && verneed.data.length > 0) {
            layout.add(verneed);
        }
        if (staticSymtab != null) {
            layout.add(staticSymtab);
        }
        layout.add(strtab);
        layout.add(shstrtab);

        for (Sect section : relocSections.values()) {
            putString(sectionNames, section.name, sectionNameOffsets);
        }
        for (GroupLayout group : groups) {
            putString(sectionNames, group.group.name, sectionNameOffsets);
        }
        for (String fixed : new String[] {".dynamic", ".dynsym", ".dynstr", ".gnu.version",
                ".gnu.version_d", ".gnu.version_r", ".symtab", ".strtab", ".shstrtab"}) {
            putString(sectionNames, fixed, sectionNameOffsets);
        }
        shstrtab.data = sectionNames.toByteArray();

        Map<Sect, Integer> indices = new LinkedHashMap<>();
        int sectionIndex = 1;
        for (Sect section : layout) {
            indices.put(section, sectionIndex++);
        }
        Map<String, Integer> namedIndex = new LinkedHashMap<>();
        indices.forEach((section, value) -> namedIndex.put(section.name, value));

        Map<String, Integer> symbolIndices = new LinkedHashMap<>();
        for (int i = 0; i < orderedSymbols.size(); i++) {
            symbolIndices.put(orderedSymbols.get(i).name, i + 1);
        }

        if (staticSymtab != null) {
            staticSymtab.data = buildSymbols(orderedSymbols, namedIndex, symbolStringOffsets, false);
        }
        if (dynsym != null) {
            dynsym.data = buildSymbols(orderedSymbols, namedIndex, dynstrOffsets, true);
        }
        for (Sect section : relocSections.values()) {
            section.data = buildRelocations(section.relDefs, symbolIndices, section.type == Constants.SHT_RELA);
        }
        for (GroupLayout group : groups) {
            group.group.data = buildGroup(group, indices);
        }
        for (Sect section : layout) {
            if (section.linkPlaceholder != null) {
                section.link = indices.get(section.linkPlaceholder);
            }
            if (section.infoPlaceholderName != null) {
                section.info = namedIndex.getOrDefault(section.infoPlaceholderName, 0);
            }
        }
        return emit(layout, indices, sectionNameOffsets);
    }

    private List<GroupLayout> buildGroups(List<Sect> inputSections) {
        Map<String, GroupLayout> groups = new LinkedHashMap<>();
        for (Sect section : inputSections) {
            if (section.comdatKey == null) {
                continue;
            }
            GroupLayout layout = groups.computeIfAbsent(section.comdatKey, key -> {
                GroupLayout created = new GroupLayout();
                created.members = new ArrayList<>();
                created.group = new Sect();
                created.group.name = ".group" + groups.size();
                created.group.type = Constants.SHT_GROUP;
                created.group.isGroup = true;
                return created;
            });
            if (section.comdatMembers != null) {
                for (String member : section.comdatMembers) {
                    if (!layout.members.contains(member)) {
                        layout.members.add(member);
                    }
                }
            } else if (!layout.members.contains(section.name)) {
                layout.members.add(section.name);
            }
        }
        return new ArrayList<>(groups.values());
    }

    private static Sect findSection(List<Sect> sections, String name) {
        for (Sect section : sections) {
            if (section.name.equals(name)) {
                return section;
            }
        }
        throw new IllegalArgumentException("unknown section " + name);
    }

    private static boolean inAnyGroup(String name, List<GroupLayout> groups) {
        for (GroupLayout group : groups) {
            if (group.members.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static void putString(ByteArrayOutputStream stream, String value, Map<String, Integer> offsets) {
        Integer existing = offsets.get(value);
        if (existing != null) {
            return;
        }
        offsets.put(value, stream.size());
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        stream.write(bytes, 0, bytes.length);
        stream.write(0);
    }

    private byte[] buildSymbols(List<SymDef> ordered, Map<String, Integer> namedSections,
                                Map<String, Integer> strOffsets, boolean dynamic) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeZeroSymbol(out);
        for (SymDef symbol : ordered) {
            int nameOffset = strOffsets.getOrDefault(symbol.name, 0);
            int bindValue;
            switch (symbol.bind) {
                case LOCAL -> bindValue = Constants.STB_LOCAL;
                case WEAK -> bindValue = Constants.STB_WEAK;
                default -> bindValue = Constants.STB_GLOBAL;
            }
            int typeValue = switch (symbol.type) {
                case OBJECT -> Constants.STT_OBJECT;
                case FUNC -> Constants.STT_FUNC;
                case SECTION -> Constants.STT_SECTION;
                default -> Constants.STT_NOTYPE;
            };
            int info = (bindValue << 4) | (typeValue & 0xf);
            int shndx;
            if (symbol.undefined) {
                shndx = Constants.SHN_UNDEF;
            } else if (symbol.common) {
                shndx = Constants.SHN_COMMON;
            } else if (symbol.section != null) {
                shndx = namedSections.getOrDefault(symbol.section, Constants.SHN_UNDEF);
            } else {
                shndx = Constants.SHN_ABS;
            }
            writeSymbol(out, nameOffset, info, symbol.visibility, shndx, symbol.value, symbol.size);
        }
        return out.toByteArray();
    }

    private void writeZeroSymbol(ByteArrayOutputStream out) {
        if (elf64) {
            for (int i = 0; i < 24; i++) {
                out.write(0);
            }
        } else {
            for (int i = 0; i < 16; i++) {
                out.write(0);
            }
        }
    }

    private void writeSymbol(ByteArrayOutputStream out, int nameOffset, int info, int visibility,
                             int shndx, long value, long size) {
        if (elf64) {
            write32(out, nameOffset);
            out.write(info);
            out.write(visibility & 0x3);
            write16(out, shndx);
            write64(out, value);
            write64(out, size);
        } else {
            write32(out, nameOffset);
            write32(out, value);
            write32(out, size);
            out.write(info);
            out.write(visibility & 0x3);
            write16(out, shndx);
        }
    }

    private byte[] buildRelocations(List<RelDef> rels, Map<String, Integer> symbolIndices, boolean rela) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (RelDef rel : rels) {
            int symbolIndex = symbolIndices.getOrDefault(rel.symbol, 0);
            if (elf64) {
                write64(out, rel.offset);
                long packed = ((long) symbolIndex << 32) | (rel.type & 0xffffffffL);
                write64(out, packed);
                if (rela) {
                    write64(out, rel.addend == null ? 0 : rel.addend);
                }
            } else {
                write32(out, (int) rel.offset);
                long packed = ((long) symbolIndex << 8) | (rel.type & 0xffL);
                write32(out, (int) packed);
                if (rela) {
                    write32(out, (int) (rel.addend == null ? 0 : rel.addend));
                }
            }
        }
        return out.toByteArray();
    }

    private byte[] buildGroup(GroupLayout layout, Map<Sect, Integer> indices) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write32(out, Constants.GRP_COMDAT);
        for (String member : layout.members) {
            Sect section = findSection(sections, member);
            write32(out, indices.get(section));
        }
        return out.toByteArray();
    }

    private byte[] buildVersym(List<SymDef> ordered, Map<String, Integer> versionIndex) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, 0);
        for (SymDef symbol : ordered) {
            int value;
            if (symbol.version == null) {
                value = symbol.undefined || symbol.bind == Bind.LOCAL
                        ? Constants.VER_NDX_GLOBAL : Constants.VER_NDX_GLOBAL;
            } else {
                value = versionIndex.get(symbol.version);
                if (symbol.nonDefault) {
                    value |= Constants.VER_NDX_HIDDEN_FLAG;
                }
            }
            write16(out, value);
        }
        return out.toByteArray();
    }

    private byte[] buildVerdef(List<Version> versions, Map<String, Integer> strings, boolean need) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Version version : versions) {
            if (version.need != need) {
                continue;
            }
            int auxCount = 1 + (version.parent != null ? 1 : 0);
            write16(out, 1);
            write16(out, 1);
            write16(out, 16);
            write16(out, auxCount);
            write32(out, 0);
            write32(out, versionIndexOf(version.name, versions));
            write32(out, 20);
            write32(out, 0);
            write32(out, strings.get(version.name));
            write32(out, version.parent == null ? 0 : 20);
            if (version.parent != null) {
                write32(out, strings.get(version.parent));
                write32(out, 0);
            }
        }
        return out.toByteArray();
    }

    private int versionIndexOf(String name, List<Version> versions) {
        int index = 2;
        for (Version version : versions) {
            if (version.name.equals(name)) {
                return index;
            }
            index++;
        }
        return index;
    }

    private byte[] buildVerneed(List<Version> versions, Map<String, Integer> strings) {
        Map<String, List<Version>> byFile = new LinkedHashMap<>();
        for (Version version : versions) {
            if (version.need) {
                byFile.computeIfAbsent(version.fromFile, key -> new ArrayList<>()).add(version);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, List<Version>> entry : byFile.entrySet()) {
            int count = entry.getValue().size();
            write16(out, 1);
            write16(out, count);
            write32(out, 0);
            write32(out, strings.get(entry.getKey()));
            write32(out, 20);
            write32(out, 0);
            for (int i = 0; i < count; i++) {
                Version version = entry.getValue().get(i);
                write32(out, 0);
                write16(out, 0);
                write16(out, versionIndexOf(version.name, versions));
                write32(out, strings.get(version.name));
                write32(out, i + 1 == count ? 0 : 16);
            }
        }
        return out.toByteArray();
    }

    private byte[] buildDynamic(Map<String, Integer> strings, boolean hasVerdef) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String library : needed) {
            writeDynamic(out, Constants.DT_NEEDED, strings.get(library));
        }
        if (soname != null) {
            writeDynamic(out, Constants.DT_SONAME, strings.get(soname));
        }
        if (hasVerdef) {
            writeDynamic(out, 0x6ffffeff, 1);
            writeDynamic(out, 0x6ffffff0, 0);
        }
        writeDynamic(out, Constants.DT_NULL, 0);
        return out.toByteArray();
    }

    private void writeDynamic(ByteArrayOutputStream out, long tag, long value) {
        if (elf64) {
            write64(out, tag);
            write64(out, value);
        } else {
            write32(out, (int) tag);
            write32(out, (int) value);
        }
    }

    private void write16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private void write32(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xff));
        out.write((int) ((value >>> 8) & 0xff));
        out.write((int) ((value >>> 16) & 0xff));
        out.write((int) ((value >>> 24) & 0xff));
    }

    private void write64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value >>> (8 * i)) & 0xff);
        }
    }

    private byte[] emit(List<Sect> layout, Map<Sect, Integer> indices,
                        Map<String, Integer> sectionNameOffsets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int headerSize = elf64 ? 64 : 52;
        int sectionHeaderSize = elf64 ? 64 : 40;
        long baseAddress = 0;
        long address = baseAddress;
        for (Sect section : layout) {
            if (section.type == Constants.SHT_GROUP || (section.flags & Constants.SHF_ALLOC) != 0) {
                address = align(address, 8);
                section.runtimeAddress = address;
                address += section.data.length;
            } else {
                section.runtimeAddress = 0;
            }
        }
        long fileOffset = headerSize;
        for (Sect section : layout) {
            fileOffset = align(fileOffset, 8);
            section.fileOffset = fileOffset;
            fileOffset += section.data.length;
        }
        fileOffset = align(fileOffset, 8);
        long sectionHeaderOffset = fileOffset;

        byte identClass = elf64 ? (byte) Constants.ELFCLASS64 : (byte) Constants.ELFCLASS32;
        out.write(0x7f);
        out.write('E');
        out.write('L');
        out.write('F');
        out.write(identClass);
        out.write(Constants.ELFDATA2LSB);
        out.write(1);
        out.write(0);
        for (int i = 8; i < 16; i++) {
            out.write(0);
        }
        write16(out, type);
        write16(out, machine);
        write32(out, 1);
        if (elf64) {
            write64(out, 0);
            write64(out, 0);
            write64(out, sectionHeaderOffset);
            write32(out, 0);
            write16(out, headerSize);
            write16(out, 0);
            write16(out, sectionHeaderSize);
            write16(out, layout.size() + 1);
            write16(out, indices.get(findSectionByName(layout, ".shstrtab")));
        } else {
            write32(out, 0);
            write32(out, 0);
            write32(out, 0);
            write32(out, sectionHeaderOffset);
            write32(out, 0);
            write16(out, headerSize);
            write16(out, 0);
            write16(out, sectionHeaderSize);
            write16(out, layout.size() + 1);
            write16(out, indices.get(findSectionByName(layout, ".shstrtab")));
        }

        for (Sect section : layout) {
            padTo(out, section.fileOffset);
            out.write(section.data, 0, section.data.length);
        }
        padTo(out, sectionHeaderOffset);

        writeSectionHeader(out, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        for (Sect section : layout) {
            int nameOffset = sectionNameOffsets.getOrDefault(section.name, 0);
            long sectionAddress = section.runtimeAddress;
            writeSectionHeader(out, section.name, nameOffset, section.type, section.flags, sectionAddress,
                    section.fileOffset, section.data.length, section.link, section.info, 1,
                    section.entsize == 0 ? 0 : section.entsize);
        }
        return out.toByteArray();
    }

    private void writeSectionHeader(ByteArrayOutputStream out, String ignoredName, int nameOffset, int type,
                                    long flags, long address, long offset, long size, int link, int info,
                                    long addralign, long entsize) {
        if (elf64) {
            write32(out, nameOffset);
            write32(out, type);
            write64(out, flags);
            write64(out, address);
            write64(out, offset);
            write64(out, size);
            write32(out, link);
            write32(out, info);
            write64(out, addralign);
            write64(out, entsize);
        } else {
            write32(out, nameOffset);
            write32(out, type);
            write32(out, flags);
            write32(out, address);
            write32(out, offset);
            write32(out, size);
            write32(out, link);
            write32(out, info);
            write32(out, addralign);
            write32(out, entsize);
        }
    }

    private static Sect findSectionByName(List<Sect> sections, String name) {
        return findSection(sections, name);
    }

    private static long align(long value, long alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    private static void padTo(ByteArrayOutputStream out, long offset) {
        while (out.size() < offset) {
            out.write(0);
        }
    }
}
