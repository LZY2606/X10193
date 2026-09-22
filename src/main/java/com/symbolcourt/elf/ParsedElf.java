package com.symbolcourt.elf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParsedElf {
    private final byte[] image;
    private final boolean elf64;
    private final boolean littleEndian;
    private final int machine;
    private final int type;
    private final String osAbi;
    private final List<Section> sections = new ArrayList<>();
    private final List<Symbol> staticSymbols = new ArrayList<>();
    private final List<Symbol> dynamicSymbols = new ArrayList<>();
    private final List<Relocation> relocations = new ArrayList<>();
    private final List<GroupInfo> groups = new ArrayList<>();
    private final VersionInfo versionInfo;
    private final Map<String, String> dynamic = new LinkedHashMap<>();
    private final List<String> needed = new ArrayList<>();
    private String soname;
    private final int entrySize;

    public ParsedElf(byte[] image, boolean elf64, boolean littleEndian, int machine, int type,
                     int dynsymCount, int entrySize) {
        this.image = image;
        this.elf64 = elf64;
        this.littleEndian = littleEndian;
        this.machine = machine;
        this.type = type;
        this.osAbi = "ELFOSABI_NONE";
        this.versionInfo = new VersionInfo(dynsymCount);
        this.entrySize = entrySize;
    }

    public byte[] image() {
        return image;
    }

    public boolean elf64() {
        return elf64;
    }

    public boolean littleEndian() {
        return littleEndian;
    }

    public int machine() {
        return machine;
    }

    public int type() {
        return type;
    }

    public int entrySize() {
        return entrySize;
    }

    public List<Section> sections() {
        return sections;
    }

    public List<Symbol> staticSymbols() {
        return staticSymbols;
    }

    public List<Symbol> dynamicSymbols() {
        return dynamicSymbols;
    }

    public List<Relocation> relocations() {
        return relocations;
    }

    public List<GroupInfo> groups() {
        return groups;
    }

    public VersionInfo versionInfo() {
        return versionInfo;
    }

    public Map<String, String> dynamic() {
        return dynamic;
    }

    public List<String> needed() {
        return needed;
    }

    public String soname() {
        return soname;
    }

    public void setSoname(String soname) {
        this.soname = soname;
    }

    public Section sectionByName(String name) {
        for (Section s : sections) {
            if (name.equals(s.name())) {
                return s;
            }
        }
        return null;
    }
}
