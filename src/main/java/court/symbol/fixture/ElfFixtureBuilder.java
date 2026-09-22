package court.symbol.fixture;

import court.symbol.elf.ParsedBinary;
import court.symbol.elf.BinaryParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElfFixtureBuilder {
    private int type = 1;
    private int machine = 62;
    private String soname = "";
    private final List<String> needed = new ArrayList<>();
    private final Map<String, SectionRequest> sections = new LinkedHashMap<>();
    private final List<SymbolRequest> symbols = new ArrayList<>();
    private final List<SymbolRequest> dynamicSymbols = new ArrayList<>();
    private final List<RelocationRequest> relocations = new ArrayList<>();
    private final List<GroupRequest> groups = new ArrayList<>();

    public ElfFixtureBuilder object() {
        type = 1;
        return this;
    }

    public ElfFixtureBuilder shared(String name) {
        type = 3;
        soname = name;
        return this;
    }

    public ElfFixtureBuilder executable() {
        type = 2;
        return this;
    }

    public ElfFixtureBuilder needed(String library) {
        needed.add(library);
        return this;
    }

    public ElfFixtureBuilder section(String name, byte[] data) {
        sections.put(name, new SectionRequest(name, 1, 0, 0, data, 1));
        return this;
    }

    public ElfFixtureBuilder section(String name, byte[] data, long flags) {
        sections.put(name, new SectionRequest(name, 1, flags, 0, data, 1));
        return this;
    }

    public ElfFixtureBuilder defined(String name, String section, int binding, int visibility,
                                    long value, long size) {
        symbols.add(new SymbolRequest(name, section, binding, visibility, 1, value, size, "", true, ""));
        return this;
    }

    public ElfFixtureBuilder undefined(String name, int binding) {
        symbols.add(new SymbolRequest(name, "UND", binding, 0, 0, 0, 0, "", true, ""));
        return this;
    }

    public ElfFixtureBuilder common(String name, long size, int binding) {
        symbols.add(new SymbolRequest(name, "COMMON", binding, 0, 0, 0, size, "", true, ""));
        return this;
    }

    public ElfFixtureBuilder group(String signature, boolean comdat, List<String> members) {
        groups.add(new GroupRequest(signature, comdat, members));
        return this;
    }

    public ElfFixtureBuilder reloc(String target, String symbol, int type, long offset, long addend) {
        relocations.add(new RelocationRequest(target, symbol, type, offset, addend, false));
        return this;
    }

    public ElfFixtureBuilder dynamicDefined(String name, int binding, int visibility, long value, long size,
                                           String version, boolean defaultVersion) {
        dynamicSymbols.add(new SymbolRequest(name, ".text", binding, visibility, 1, value, size,
                version, defaultVersion, ""));
        return this;
    }

    public ElfFixtureBuilder dynamicDefined(String name, String section, int binding, int visibility,
                                           long value, long size, String version, boolean defaultVersion) {
        dynamicSymbols.add(new SymbolRequest(name, section, binding, visibility, 1, value, size,
                version, defaultVersion, ""));
        return this;
    }

    public ElfFixtureBuilder dynamicNeed(String name, String library, String version, boolean defaultVersion) {
        dynamicSymbols.add(new SymbolRequest(name, "UND", 1, 0, 0, 0, 0, version, defaultVersion, library));
        return this;
    }

    public ElfFixtureBuilder dynamicReloc(String target, String symbol, int type, long offset, long addend) {
        relocations.add(new RelocationRequest(target, symbol, type, offset, addend, true));
        return this;
    }

    public ParsedBinary buildParsed(String name) {
        return new BinaryParser().parse(build(), name);
    }

    public byte[] build() {
        BuildState state = new BuildState();
        for (String name : sections.keySet()) {
            state.addSection(name, sections.get(name).data);
        }
        for (GroupRequest group : groups) {
            state.addSection(".group." + group.signature, new byte[0]);
        }
        if (!symbols.isEmpty()) {
            state.addSection(".strtab", new byte[0]);
            state.addSection(".symtab", new byte[0]);
        }
        if (type != 1 || !dynamicSymbols.isEmpty() || !needed.isEmpty() || !soname.isEmpty()) {
            state.addSection(".dynstr", new byte[0]);
            state.addSection(".dynsym", new byte[0]);
            if (dynamicSymbols.stream().anyMatch(symbol -> !symbol.version().isEmpty())) {
                state.addSection(".gnu.version", new byte[0]);
            }
            if (dynamicSymbols.stream().anyMatch(symbol -> !symbol.version().isEmpty() && symbol.library().isEmpty())) {
                state.addSection(".gnu.version_d", new byte[0]);
            }
            if (dynamicSymbols.stream().anyMatch(symbol -> !symbol.library().isEmpty())) {
                state.addSection(".gnu.version_r", new byte[0]);
            }
            state.addSection(".dynamic", new byte[0]);
        }
        for (RelocationRequest relocation : relocations) {
            state.addSection(relocation.dynamic() ? ".rela.dyn" : ".rela" + relocation.target(), new byte[0]);
        }
        state.addSection(".shstrtab", new byte[0]);
        buildStringTables(state);
        buildSymbols(state);
        buildGroups(state);
        buildDynamic(state);
        buildRelocations(state);
        return assemble(state);
    }

    private void buildStringTables(BuildState state) {
        sections.forEach((name, request) -> {
            state.addSection(name, request.data);
            state.sectionFlags.put(name, request.flags);
        });
        state.sectionData.put(".shstrtab", state.stringTable(state.sectionNames));
        if (state.sectionData.containsKey(".strtab")) {
            state.sectionData.put(".strtab", state.stringTable(symbols.stream().map(SymbolRequest::name).toList()));
        }
        List<String> dynamicStrings = new ArrayList<>();
        dynamicStrings.add("");
        dynamicStrings.add(soname);
        dynamicStrings.addAll(needed);
        dynamicSymbols.forEach(symbol -> {
            dynamicStrings.add(symbol.name());
            if (!symbol.version().isEmpty()) {
                dynamicStrings.add(symbol.version());
            }
            if (!symbol.library().isEmpty()) {
                dynamicStrings.add(symbol.library());
            }
        });
        if (state.sectionData.containsKey(".dynstr")) {
            state.sectionData.put(".dynstr", state.stringTable(dynamicStrings));
        }
    }

    private void buildSymbols(BuildState state) {
        if (!symbols.isEmpty()) {
            List<SymbolRequest> ordered = new ArrayList<>();
            ordered.add(SymbolRequest.NULL);
            symbols.stream().filter(symbol -> symbol.binding() == 0).forEach(ordered::add);
            symbols.stream().filter(symbol -> symbol.binding() != 0).forEach(ordered::add);
            state.staticSymbolIndex.clear();
            ordered.forEach(symbol -> state.staticSymbolIndex.put(symbol.name() + ":" + symbol.section(),
                    state.staticSymbolIndex.size()));
            state.sectionData.put(".symtab", symbolTable(ordered, state, false));
            state.sectionTypes.put(".symtab", 2);
            state.sectionLinks.put(".symtab", state.indexOf(".strtab"));
            state.sectionInfos.put(".symtab", 1 + (int) ordered.stream().skip(1).filter(symbol -> symbol.binding() == 0).count());
            state.entrySizes.put(".symtab", 24L);
        }
        if (state.sectionData.containsKey(".dynsym")) {
            List<SymbolRequest> ordered = new ArrayList<>();
            ordered.add(SymbolRequest.NULL);
            dynamicSymbols.stream().filter(symbol -> symbol.binding() == 0).forEach(ordered::add);
            dynamicSymbols.stream().filter(symbol -> symbol.binding() != 0).forEach(ordered::add);
            state.dynamicSymbolIndex.clear();
            for (int i = 0; i < ordered.size(); i++) {
                state.dynamicSymbolIndex.put(ordered.get(i).name() + ":" + ordered.get(i).version() + ":"
                        + ordered.get(i).library(), i);
            }
            state.sectionData.put(".dynsym", symbolTable(ordered, state, true));
            state.sectionTypes.put(".dynsym", 11);
            state.sectionLinks.put(".dynsym", state.indexOf(".dynstr"));
            state.sectionInfos.put(".dynsym", 1 + (int) ordered.stream().skip(1).filter(symbol -> symbol.binding() == 0).count());
            state.entrySizes.put(".dynsym", 24L);
            buildVersionSections(state, ordered);
        }
    }

    private byte[] symbolTable(List<SymbolRequest> ordered, BuildState state, boolean dynamic) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int index = 0; index < ordered.size(); index++) {
            SymbolRequest symbol = ordered.get(index);
            int sectionIndex = switch (symbol.section()) {
                case "UND" -> 0;
                case "ABS" -> 0xfff1;
                case "COMMON" -> 0xfff2;
                default -> state.indexOf(symbol.section());
            };
            if (index == 0) {
                sectionIndex = 0;
            }
            String stringsSection = dynamic ? ".dynstr" : ".strtab";
            int nameOffset = index == 0 ? 0 : state.stringOffset(stringsSection, symbol.name());
            int info = symbol.binding() << 4 | symbol.type() & 0x0f;
            output.writeBytes(le32(nameOffset));
            output.writeBytes(new byte[]{(byte) info, (byte) symbol.visibility()});
            output.writeBytes(le16(sectionIndex));
            output.writeBytes(le64(symbol.value()));
            output.writeBytes(le64(symbol.size()));
        }
        return output.toByteArray();
    }

    private void buildVersionSections(BuildState state, List<SymbolRequest> ordered) {
        List<Map.Entry<String, Integer>> definedVersions = new ArrayList<>();
        Map<String, Map<String, Integer>> requiredVersions = new LinkedHashMap<>();
        int nextIndex = 2;
        for (SymbolRequest symbol : ordered.subList(1, ordered.size())) {
            if (symbol.version().isEmpty()) {
                continue;
            }
            if (symbol.library().isEmpty()) {
                definedVersions.add(Map.entry(symbol.version(), nextIndex++));
            } else {
                requiredVersions.computeIfAbsent(symbol.library(), ignored -> new LinkedHashMap<>());
                if (!requiredVersions.get(symbol.library()).containsKey(symbol.version())) {
                    requiredVersions.get(symbol.library()).put(symbol.version(), nextIndex++);
                }
            }
        }
        ByteArrayOutputStream versym = new ByteArrayOutputStream();
        versym.writeBytes(le16(0));
        for (SymbolRequest symbol : ordered.subList(1, ordered.size())) {
            int index = 1;
            if (!symbol.version().isEmpty()) {
                index = symbol.library().isEmpty()
                        ? definedVersions.get((int) ordered.subList(1, ordered.size()).indexOf(symbol)).getValue()
                        : requiredVersions.get(symbol.library()).get(symbol.version());
                if (!symbol.defaultVersion()) {
                    index |= 0x8000;
                }
            }
            versym.writeBytes(le16(index));
        }
        state.sectionData.put(".gnu.version", versym.toByteArray());
        state.sectionTypes.put(".gnu.version", 0x6fffffff);
        state.sectionLinks.put(".gnu.version", state.indexOf(".dynsym"));
        state.entrySizes.put(".gnu.version", 2L);
        if (state.sectionData.containsKey(".gnu.version_d")) {
            ByteArrayOutputStream verdef = new ByteArrayOutputStream();
            int ordinal = 0;
            for (Map.Entry<String, Integer> entry : definedVersions) {
                int flags = !isDefaultVersion(entry.getKey()) ? 2 : 0;
                int next = ordinal + 1 < definedVersions.size() ? 28 : 0;
                verdef.writeBytes(le16(1));
                verdef.writeBytes(le16(flags));
                verdef.writeBytes(le16(entry.getValue()));
                verdef.writeBytes(le16(1));
                verdef.writeBytes(le32(0));
                verdef.writeBytes(le32(20));
                verdef.writeBytes(le32(next));
                verdef.writeBytes(le32(state.stringOffset(".dynstr", entry.getKey())));
                verdef.writeBytes(le32(0));
                ordinal++;
            }
            state.sectionData.put(".gnu.version_d", verdef.toByteArray());
            state.sectionTypes.put(".gnu.version_d", 0x6ffffffd);
            state.sectionLinks.put(".gnu.version_d", state.indexOf(".dynstr"));
        }
        if (state.sectionData.containsKey(".gnu.version_r")) {
            ByteArrayOutputStream verneed = new ByteArrayOutputStream();
            int needIndex = 0;
            for (Map.Entry<String, Map<String, Integer>> libraryEntry : requiredVersions.entrySet()) {
                int auxCount = libraryEntry.getValue().size();
                int needBytes = 16 + auxCount * 16;
                verneed.writeBytes(le16(1));
                verneed.writeBytes(le16(auxCount));
                verneed.writeBytes(le32(state.stringOffset(".dynstr", libraryEntry.getKey())));
                verneed.writeBytes(le32(16));
                verneed.writeBytes(le32(needIndex + 1 < requiredVersions.size() ? needBytes : 0));
                int ordinal = 0;
                for (Map.Entry<String, Integer> versionEntry : libraryEntry.getValue().entrySet()) {
                    verneed.writeBytes(le32(0x456e7821));
                    verneed.writeBytes(le16(0));
                    verneed.writeBytes(le16(isDefaultVersion(versionEntry.getKey()) ? 0 : 2));
                    verneed.writeBytes(le16(versionEntry.getValue()));
                    verneed.writeBytes(le16(0));
                    verneed.writeBytes(le32(state.stringOffset(".dynstr", versionEntry.getKey())));
                    verneed.writeBytes(le32(++ordinal < auxCount ? 16 : 0));
                }
                needIndex++;
            }
            state.sectionData.put(".gnu.version_r", verneed.toByteArray());
            state.sectionTypes.put(".gnu.version_r", 0x6ffffffe);
            state.sectionLinks.put(".gnu.version_r", state.indexOf(".dynstr"));
        }
    }

    private boolean isDefaultVersion(String version) {
        return !version.endsWith(":old") && !version.startsWith("OLD_");
    }

    private void buildGroups(BuildState state) {
        for (GroupRequest group : groups) {
            if (!state.staticSymbolIndex.containsKey(group.signature() + ":.group." + group.signature())) {
                addGroupSignature(group.signature());
                state.sectionData.put(".symtab", rebuildStaticSymbols(state));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.writeBytes(le32(group.comdat() ? 2 : 1));
            for (String member : group.members()) {
                output.writeBytes(le32(state.indexOf(member)));
            }
            String groupSection = ".group." + group.signature();
            state.sectionData.put(groupSection, output.toByteArray());
            state.sectionTypes.put(groupSection, 17);
            state.sectionLinks.put(groupSection, state.indexOf(".symtab"));
            state.sectionInfos.put(groupSection, state.staticSymbolIndex.get(group.signature()
                    + ":.group." + group.signature()));
            state.entrySizes.put(groupSection, 4L);
        }
        if (!groups.isEmpty() && !symbols.isEmpty()) {
            state.sectionData.put(".symtab", rebuildStaticSymbols(state));
        }
    }

    private void addGroupSignature(String signature) {
        symbols.add(new SymbolRequest(signature, ".group." + signature, 0, 0, 0, 0, 0,
                "", true, ""));
    }

    private byte[] rebuildStaticSymbols(BuildState state) {
        List<SymbolRequest> ordered = new ArrayList<>();
        ordered.add(SymbolRequest.NULL);
        symbols.stream().filter(symbol -> symbol.binding() == 0).forEach(ordered::add);
        symbols.stream().filter(symbol -> symbol.binding() != 0).forEach(ordered::add);
        state.staticSymbolIndex.clear();
        ordered.forEach(symbol -> state.staticSymbolIndex.put(symbol.name() + ":" + symbol.section(),
                state.staticSymbolIndex.size()));
        state.sectionData.put(".strtab", state.stringTable(symbols.stream().map(SymbolRequest::name).toList()));
        state.sectionInfos.put(".symtab",
                1 + (int) ordered.stream().skip(1).filter(symbol -> symbol.binding() == 0).count());
        byte[] table = symbolTable(ordered, state, false);
        state.entrySizes.put(".symtab", 24L);
        return table;
    }

    private void buildDynamic(BuildState state) {
        if (!state.sectionData.containsKey(".dynamic")) {
            return;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (String library : needed) {
            writeDynamic(output, 1, state.stringOffset(".dynstr", library));
        }
        if (!soname.isEmpty()) {
            writeDynamic(output, 14, state.stringOffset(".dynstr", soname));
        }
        if (state.sectionData.containsKey(".gnu.version")) {
            writeDynamic(output, 0x6fffffff, state.indexOf(".gnu.version"));
        }
        writeDynamic(output, 5, 0);
        writeDynamic(output, 6, state.indexOf(".dynsym"));
        writeDynamic(output, 0, 0);
        state.sectionData.put(".dynamic", output.toByteArray());
        state.sectionTypes.put(".dynamic", 6);
        state.sectionLinks.put(".dynamic", state.indexOf(".dynstr"));
        state.entrySizes.put(".dynamic", 16L);
    }

    private void writeDynamic(ByteArrayOutputStream output, int tag, long value) {
        output.writeBytes(le64(Integer.toUnsignedLong(tag)));
        output.writeBytes(le64(value));
    }

    private void buildRelocations(BuildState state) {
        for (RelocationRequest relocation : relocations) {
            String sectionName = relocation.dynamic() ? ".rela.dyn" : ".rela" + relocation.target();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Map<String, Integer> indexes = relocation.dynamic() ? state.dynamicSymbolIndex : state.staticSymbolIndex;
            String key = relocation.dynamic()
                    ? relocation.symbol() + "::"
                    : relocation.symbol() + ":UND";
            Integer index = indexes.get(key);
            if (index == null) {
                index = indexes.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith(relocation.symbol() + ":"))
                        .map(Map.Entry::getValue).findFirst().orElseThrow();
            }
            output.writeBytes(le64(relocation.offset()));
            output.writeBytes(le64((long) index << 32 | relocation.type()));
            output.writeBytes(le64(relocation.addend()));
            state.sectionData.put(sectionName, output.toByteArray());
            state.sectionTypes.put(sectionName, 4);
            state.sectionLinks.put(sectionName, state.indexOf(relocation.dynamic() ? ".dynsym" : ".symtab"));
            state.sectionInfos.put(sectionName, state.indexOf(relocation.target()));
            state.entrySizes.put(sectionName, 24L);
        }
    }

    private byte[] assemble(BuildState state) {
        state.sectionData.put(".shstrtab", state.stringTable(state.sectionNames));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[64];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = 2;
        header[5] = 1;
        header[6] = 1;
        write(header, 16, le16(type));
        write(header, 18, le16(machine));
        write(header, 20, le32(1));
        write(header, 52, le16(64));
        write(header, 58, le16(64));
        write(header, 60, le16(state.sectionNames.size()));
        write(header, 62, le16(state.indexOf(".shstrtab")));
        output.writeBytes(header);
        long[] offsets = new long[state.sectionNames.size()];
        long[] sizes = new long[state.sectionNames.size()];
        for (int i = 1; i < state.sectionNames.size(); i++) {
            String name = state.sectionNames.get(i);
            byte[] data = state.sectionData.get(name);
            while (output.size() % 8 != 0) {
                output.write(0);
            }
            offsets[i] = output.size();
            sizes[i] = data.length;
            output.writeBytes(data);
        }
        while (output.size() % 8 != 0) {
            output.write(0);
        }
        long sectionTableOffset = output.size();
        for (int i = 0; i < state.sectionNames.size(); i++) {
            String name = i == 0 ? "" : state.sectionNames.get(i);
            int sectionType = i == 0 ? 0 : state.sectionTypes.getOrDefault(name, 1);
            long flags = i == 0 ? 0 : state.sectionFlags.getOrDefault(name, 0L);
            int link = i == 0 ? 0 : state.sectionLinks.getOrDefault(name, 0);
            int info = i == 0 ? 0 : state.sectionInfos.getOrDefault(name, 0);
            long entrySize = i == 0 ? 0 : state.entrySizes.getOrDefault(name, 0L);
            output.writeBytes(le32(i == 0 ? 0 : state.stringOffset(".shstrtab", name)));
            output.writeBytes(le32(sectionType));
            output.writeBytes(le64(flags));
            output.writeBytes(le64(0));
            output.writeBytes(le64(offsets[i]));
            output.writeBytes(le64(sizes[i]));
            output.writeBytes(le32((int) link));
            output.writeBytes(le32((int) info));
            output.writeBytes(le64(0));
            output.writeBytes(le64(entrySize));
        }
        byte[] result = output.toByteArray();
        write(header, 40, le64(sectionTableOffset));
        System.arraycopy(header, 0, result, 0, 64);
        return result;
    }

    private void write(byte[] target, int offset, byte[] values) {
        System.arraycopy(values, 0, target, offset, values.length);
    }

    private byte[] le16(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }

    private byte[] le32(long value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private byte[] le64(long value) {
        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte) (value >>> i * 8);
        }
        return result;
    }

    private record SectionRequest(String name, int type, long flags, long address, byte[] data, long entrySize) {
    }

    private record SymbolRequest(String name, String section, int binding, int visibility, int type, long value,
                                 long size, String version, boolean defaultVersion, String library) {
        static final SymbolRequest NULL = new SymbolRequest("", "UND", 0, 0, 0, 0, 0, "", true, "");
    }

    private record RelocationRequest(String target, String symbol, int type, long offset, long addend,
                                     boolean dynamic) {
    }

    private record GroupRequest(String signature, boolean comdat, List<String> members) {
    }

    private static final class BuildState {
        private final List<String> sectionNames = new ArrayList<>(List.of(""));
        private final Map<String, byte[]> sectionData = new LinkedHashMap<>();
        private final Map<String, Integer> sectionTypes = new LinkedHashMap<>();
        private final Map<String, Long> sectionFlags = new LinkedHashMap<>();
        private final Map<String, Integer> sectionLinks = new LinkedHashMap<>();
        private final Map<String, Integer> sectionInfos = new LinkedHashMap<>();
        private final Map<String, Long> entrySizes = new LinkedHashMap<>();
        private final Map<String, Integer> staticSymbolIndex = new LinkedHashMap<>();
        private final Map<String, Integer> dynamicSymbolIndex = new LinkedHashMap<>();

        {
            sectionData.put("", new byte[0]);
            sectionTypes.put("", 0);
        }

        private void addSection(String name, byte[] data) {
            if (!sectionNames.contains(name)) {
                sectionNames.add(name);
                sectionData.put(name, data);
                if (name.endsWith("strtab") || name.equals(".dynstr")) {
                    sectionTypes.put(name, 3);
                }
            }
        }

        private int indexOf(String name) {
            int index = sectionNames.indexOf(name);
            if (index < 0) {
                throw new IllegalArgumentException("缺少 section: " + name);
            }
            return index;
        }

        private byte[] stringTable(List<String> values) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(0);
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                output.writeBytes(value.getBytes(StandardCharsets.UTF_8));
                output.write(0);
            }
            return output.toByteArray();
        }

        private int stringOffset(String section, String value) {
            byte[] table = sectionData.get(section);
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i <= table.length - bytes.length; i++) {
                boolean match = true;
                for (int j = 0; j < bytes.length; j++) {
                    if (table[i + j] != bytes[j]) {
                        match = false;
                        break;
                    }
                }
                if (match && (i == 0 || table[i - 1] == 0)) {
                    return i;
                }
            }
            throw new IllegalArgumentException("字符串不在表中: " + value);
        }
    }
}
