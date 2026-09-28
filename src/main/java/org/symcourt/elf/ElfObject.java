package org.symcourt.elf;

import java.util.ArrayList;
import java.util.List;

/** Fully parsed view of one ELF object. */
public final class ElfObject {
    public String sourceName;
    public String displayName;
    public boolean elf64;
    public boolean littleEndian;
    public int type;
    public int machine;
    public int flags;
    public long entry;

    public final List<ElfSection> sections = new ArrayList<>();
    public final List<Symbol> symtab = new ArrayList<>();
    public final List<Symbol> dynsym = new ArrayList<>();
    public final List<Reloc> staticRelocs = new ArrayList<>();
    public final List<Reloc> dynamicRelocs = new ArrayList<>();
    public final List<SectionGroup> groups = new ArrayList<>();
    public final List<VerDefNode> verdefs = new ArrayList<>();
    public final List<VerNeedEntry> verneeds = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;
    public boolean symbolic;

    public ElfSection section(int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        return sections.get(index);
    }

    public List<Symbol> allSymbols() {
        List<Symbol> all = new ArrayList<>(symtab.size() + dynsym.size());
        all.addAll(symtab);
        all.addAll(dynsym);
        return all;
    }

    public String typeLabel() {
        return Elf.typeName(type);
    }
}
