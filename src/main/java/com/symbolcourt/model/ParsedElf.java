package com.symbolcourt.model;

import com.symbolcourt.model.Enums.*;

import java.util.ArrayList;
import java.util.List;

public class ParsedElf {
    public String origin;
    public String displayName;
    public ElfClass elfClass;
    public Endian endian;
    public Machine machine = Machine.GENERIC;
    public ObjectKind kind = ObjectKind.UNKNOWN;
    public int machineValue;
    public String soname;
    public String interpreter;
    public List<ElfSection> sections = new ArrayList<>();
    public List<ElfSymbol> symbols = new ArrayList<>();
    public List<ElfSymbol> dynamicSymbols = new ArrayList<>();
    public List<ElfRelocation> relocations = new ArrayList<>();
    public List<VersionDef> versionDefs = new ArrayList<>();
    public List<VersionNeed> versionNeeds = new ArrayList<>();
    public List<GroupInfo> groups = new ArrayList<>();
    public List<String> needed = new ArrayList<>();
    public boolean corrupt;
    public String error;

    public boolean isShared() {
        return kind == ObjectKind.SHARED;
    }

    public ElfSection sectionAt(int index) {
        if (index < 0 || index >= sections.size()) return null;
        return sections.get(index);
    }
}
