package com.symtribunal.demo;

import com.symtribunal.elf.Elf;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constructs small, structurally valid ELF64 little-endian objects in memory. The output is
 * intentionally minimal but includes the structures the tribunal must reason about:
 * symtab/dynsym, versym/verdef/verneed, SHT_GROUP COMDAT and RELA relocations.
 */
public class ElfFileBuilder {

    public static class SectionData {
        String name;
        int type;
        long flags;
        byte[] data = new byte[0];
        long address;
        long align = 1;
        long entsize;
        int link = 0;
        int info = 0;
    }

    public static class VersionDefSpec {
        String name;
        boolean base;
        List<String> aliases = new ArrayList<>();
    }

    public static class VersionNeedSpec {
        String file;
        List<NeedEntry> entries = new ArrayList<>();
    }

    public static class NeedEntry {
        String name;
        boolean weak;
    }

    private int type = Elf.ET_REL;
    private int machine = Elf.EM_X86_64;
    private final Map<String, SectionData> sections = new LinkedHashMap<>();
    private final List<SymbolSpec> symbols = new ArrayList<>();
    private boolean dynamicSymbols;
    private String soname;
    private final List<String> needed = new ArrayList<>();
    private final List<VersionDefSpec> defs = new ArrayList<>();
    private final List<VersionNeedSpec> needs = new ArrayList<>();
    private final Map<String, List<RelocSpec>> relocations = new LinkedHashMap<>();
    private String comdatSignature;
    private List<String> comdatSections = new ArrayList<>();

    public ElfFileBuilder rel() {
        this.type = Elf.ET_REL;
        return this;
    }

    public ElfFileBuilder dyn() {
        this.type = Elf.ET_DYN;
        this.dynamicSymbols = true;
        return this;
    }

    public ElfFileBuilder exec() {
        this.type = Elf.ET_EXEC;
        this.dynamicSymbols = true;
        return this;
    }

    public ElfFileBuilder machine(int machine) {
        this.machine = machine;
        return this;
    }

    public ElfFileBuilder soname(String soname) {
        this.soname = soname;
        return this;
    }

    public ElfFileBuilder needed(String library) {
        this.needed.add(library);
        return this;
    }

    public ElfFileBuilder section(String name, byte[] data, long flags) {
        SectionData section = new SectionData();
        section.name = name;
        section.type = Elf.SHT_PROGBITS;
        section.data = data;
        section.flags = flags;
        sections.put(name, section);
        return this;
    }

    public ElfFileBuilder section(String name, int size, long flags) {
        return section(name, new byte[size], flags);
    }

    public ElfFileBuilder symbol(SymbolSpec spec) {
        symbols.add(spec);
        return this;
    }

    public ElfFileBuilder versionDef(String name, String... aliases) {
        VersionDefSpec spec = new VersionDefSpec();
        spec.name = name;
        spec.aliases.addAll(List.of(aliases));
        defs.add(spec);
        return this;
    }

    public ElfFileBuilder versionBase(String sonameBase) {
        VersionDefSpec spec = new VersionDefSpec();
        spec.name = sonameBase;
        spec.base = true;
        defs.add(spec);
        return this;
    }

    public ElfFileBuilder versionNeed(String file, String name, boolean weak) {
        VersionNeedSpec found = needs.stream().filter(n -> n.file.equals(file)).findFirst()
                .orElseGet(() -> {
                    VersionNeedSpec created = new VersionNeedSpec();
                    created.file = file;
                    needs.add(created);
                    return created;
                });
        NeedEntry entry = new NeedEntry();
        entry.name = name;
        entry.weak = weak;
        found.entries.add(entry);
        return this;
    }

    public ElfFileBuilder relocation(String targetSection, RelocSpec reloc) {
        relocations.computeIfAbsent(targetSection, k -> new ArrayList<>()).add(reloc);
        return this;
    }

    public ElfFileBuilder comdat(String signature, List<String> members) {
        this.comdatSignature = signature;
        this.comdatSections = new ArrayList<>(members);
        return this;
    }

    public byte[] build() {
        List<String> symbolNames = new ArrayList<>();
        symbolNames.add("");
        Map<String, Integer> symbolNameIndex = new LinkedHashMap<>();
        for (SymbolSpec spec : symbols) {
            if (spec.name == null || spec.name.isEmpty() || symbolNameIndex.containsKey(spec.name)) {
                continue;
            }
            symbolNameIndex.put(spec.name, symbolNames.size());
            symbolNames.add(spec.name);
        }

        List<SectionData> ordered = new ArrayList<>();
        SectionData nullSection = new SectionData();
        nullSection.name = "";
        ordered.add(nullSection);

        List<SectionData> realSections = new ArrayList<>(sections.values());
        ordered.addAll(realSections);

        // COMDAT signature must appear as a symbol; synthesize a local one if missing.
        if (comdatSignature != null && symbolNameIndex.get(comdatSignature) == null) {
            symbolNameIndex.put(comdatSignature, symbolNames.size());
            symbolNames.add(comdatSignature);
        }

        SectionData strtab = new SectionData();
        strtab.name = ".strtab";
        strtab.type = Elf.SHT_STRTAB;
        strtab.data = encodeStrings(symbolNames);
        ordered.add(strtab);

        SectionData dynstr = null;
        List<String> dynamicStrings = new ArrayList<>();
        dynamicStrings.add("");
        if (dynamicSymbols) {
            dynstr = new SectionData();
            dynstr.name = ".dynstr";
            dynstr.type = Elf.SHT_STRTAB;
            if (soname != null) {
                dynamicStrings.add(soname);
            }
            for (String library : needed) {
                dynamicStrings.add(library);
            }
            ordered.add(dynstr);
        }

        SectionData symtab = new SectionData();
        symtab.name = ".symtab";
        symtab.type = Elf.SHT_SYMTAB;
        symtab.entsize = 24;
        symtab.link = indexOf(ordered, strtab);
        ordered.add(symtab);

        SectionData dynsym = null;
        if (dynamicSymbols) {
            dynsym = new SectionData();
            dynsym.name = ".dynsym";
            dynsym.type = Elf.SHT_DYNSYM;
            dynsym.entsize = 24;
            dynsym.link = indexOf(ordered, dynstr);
            ordered.add(dynsym);
        }

        // Version metadata sections (allocated only in dynamic objects).
        SectionData verdef = defs.isEmpty() ? null : addNamed(ordered, ".gnu.version_d",
                Elf.SHT_GNU_verdef, Elf.SHF_ALLOC);
        SectionData verneed = needs.isEmpty() ? null : addNamed(ordered, ".gnu.version_r",
                Elf.SHT_GNU_verneed, Elf.SHF_ALLOC);
        SectionData versym = dynamicSymbols ? addNamed(ordered, ".gnu.version",
                Elf.SHT_GNU_versym, Elf.SHF_ALLOC) : null;
        if (versym != null) {
            versym.entsize = 2;
            versym.link = indexOf(ordered, dynsym);
        }
        if (verdef != null) {
            verdef.link = indexOf(ordered, dynstr);
        }
        if (verneed != null) {
            verneed.link = indexOf(ordered, dynstr);
        }

        SectionData group = null;
        if (comdatSignature != null) {
            group = addNamed(ordered, ".group", Elf.SHT_GROUP, 0);
            group.link = indexOf(ordered, symtab);
        }

        List<SectionData> relocSections = new ArrayList<>();
        for (String target : relocations.keySet()) {
            SectionData reloc = addNamed(ordered, ".rela" + target, Elf.SHT_RELA, 0);
            reloc.entsize = 24;
            reloc.link = indexOf(ordered, dynamicSymbols && type != Elf.ET_REL ? dynsym : symtab);
            reloc.info = indexOf(ordered, sections.get(target));
            relocSections.add(reloc);
        }

        SectionData dynamic = null;
        if (dynamicSymbols) {
            dynamic = addNamed(ordered, ".dynamic", Elf.SHT_DYNAMIC, Elf.SHF_ALLOC | 0x3);
            dynamic.entsize = 16;
            dynamic.link = indexOf(ordered, dynstr);
            ordered.add(dynamic);
        }

        SectionData shstrtab = new SectionData();
        shstrtab.name = ".shstrtab";
        shstrtab.type = Elf.SHT_STRTAB;
        List<String> sectionNames = new ArrayList<>();
        sectionNames.add("");
        for (SectionData section : ordered) {
            if (!section.name.isEmpty()) {
                section.nameOffset = addString(sectionNames, section.name);
            }
        }
        shstrtab.data = encodeStrings(sectionNames);
        ordered.add(shstrtab);

        // Build symbol table images.
        int localCount = buildSymtab(symtab, symbols, symbolNameIndex, ordered,
                comdatSignature, group, false, type);
        symtab.info = localCount;
        List<SymbolSpec> dynamicList = symbols;
        if (dynamicSymbols) {
            int dynLocal = buildSymtab(dynsym, dynamicList, symbolNameIndex, ordered,
                    comdatSignature, group, true, type);
            dynsym.info = dynLocal;
        }

        // Version strings live in .dynstr; append them now then rebuild dynsym name offsets only
        // for version sections (symbol names for dynsym are shared from .dynstr in real objects).
        if (dynamicSymbols) {
            for (VersionDefSpec def : defs) {
                def.nameOffset = addString(dynamicStrings, def.name);
                for (String alias : def.aliases) {
                    addString(dynamicStrings, alias);
                }
            }
            for (VersionNeedSpec need : needs) {
                need.fileOffset = addString(dynamicStrings, need.file);
                for (NeedEntry entry : need.entries) {
                    entry.nameOffset = addString(dynamicStrings, entry.name);
                }
            }
            dynstr.data = encodeStrings(dynamicStrings);
            rebuildDynsymNames(dynsym, dynamicList, dynamicStrings);
        }

        if (versym != null) {
            buildVersym(versym, dynamicList, defs, needs);
        }
        if (verdef != null) {
            verdef.data = buildVerdef(defs);
        }
        if (verneed != null) {
            verneed.data = buildVerneed(needs);
        }
        if (group != null) {
            buildGroup(group, comdatSignature, comdatSections, ordered, symbolNameIndex);
        }
        int relocIndex = 0;
        for (Map.Entry<String, List<RelocSpec>> entry : relocations.entrySet()) {
            SectionData reloc = relocSections.get(relocIndex++);
            buildRela(reloc, entry.getValue(), dynamicList, dynamicSymbols && type != Elf.ET_REL);
        }
        if (dynamic != null) {
            dynamic.data = buildDynamic(ordered, dynstr, dynsym, verdef, verneed, versym);
        }

        return emit(ordered, machine, type);
    }
}
