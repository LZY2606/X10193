package com.gsb.tribunal.elf;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ElfParser {

    public static boolean isElf(byte[] d) {
        return d.length >= 4 && (d[0] & 0xff) == 0x7f && d[1] == 'E' && d[2] == 'L' && d[3] == 'F';
    }

    public static ElfFile parse(String name, byte[] data) throws ElfException {
        if (!isElf(data)) {
            throw new ElfException("missing ELF magic in " + name);
        }
        int elfClass = data[4] & 0xff;
        int dataEncoding = data[5] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfException("invalid EI_CLASS " + elfClass);
        }
        if (dataEncoding != 1 && dataEncoding != 2) {
            throw new ElfException("invalid EI_DATA " + dataEncoding);
        }
        boolean elf64 = elfClass == 2;
        Cursor c = new Cursor(data, dataEncoding == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        int type = c.u2(16);
        int machine = c.u2(18);
        long shoff = c.word(elf64 ? 40 : 32, elf64);
        int shentsize = c.u2(elf64 ? 58 : 46);
        int shnum = c.u2(elf64 ? 60 : 48);
        int shstrndx = c.u2(elf64 ? 62 : 50);

        if (shoff == 0) {
            throw new ElfException("no section headers in " + name);
        }
        int fixedEntSize = elf64 ? 64 : 40;
        if (shentsize < fixedEntSize) {
            throw new ElfException("e_shentsize " + shentsize + " too small");
        }
        // Extended section numbering: real values live in section 0.
        if (shnum == 0 || shstrndx == 0xffff) {
            long h0 = shoff;
            shnum = (int) c.word(h0 + (elf64 ? 32 : 20), elf64);
            shstrndx = c.u4(h0 + (elf64 ? 40 : 24)).intValue();
        }
        if (shnum <= 0) {
            throw new ElfException("no sections");
        }
        c.require(shoff, (long) shentsize * shnum);
        if (shstrndx < 0 || shstrndx >= shnum) {
            throw new ElfException("e_shstrndx " + shstrndx + " out of range (shnum=" + shnum + ")");
        }

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            long base = shoff + (long) i * shentsize;
            int stype = c.u4(base).intValue();
            long flags = c.word(base + 4, elf64);
            long addr = c.word(base + 8, elf64);
            long offset = c.word(base + 16, elf64);
            long size = c.word(base + 24, elf64);
            int link = c.u4(base + 32).intValue();
            int info = c.u4(base + 36).intValue();
            long align = c.word(base + 40, elf64);
            long entsize = c.word(base + (elf64 ? 48 : 40), elf64);
            sections.add(new Section(i, "", stype, flags, addr, offset, size, link, info, align, entsize));
            if (offset > 0 && size > 0) {
                c.require(offset, size);
            }
        }
        Section shstr = sections.get(shstrndx);
        if (shstr.size() > 0) {
            c.require(shstr.offset, shstr.size);
        }
        List<Section> named = new ArrayList<>();
        for (Section s : sections) {
            String sname = s.type() == Section.SHT_NULL ? "" : c.cstring(shstr.offset, shstr.size, c.u4(s.shoffNamePlaceholder()));
            named.add(s);
        }
        sections = nameSections(c, sections, shstr);

        List<VerDef> verDefs = parseVerDefs(c, elf64, sections);
        List<VerNeed> verNeeds = parseVerNeeds(c, elf64, sections);

        List<Symbol> symtab = List.of();
        List<Symbol> dynsym = List.of();
        for (Section s : sections) {
            if (s.type() == Section.SHT_SYMTAB) {
                symtab = parseSymbols(c, elf64, sections, s, verDefs, verNeeds, false);
            } else if (s.type() == Section.SHT_DYNSYM) {
                dynsym = parseSymbols(c, elf64, sections, s, verDefs, verNeeds, true);
            }
        }

        List<Group> groups = parseGroups(c, elf64, sections, symtab);
        Map<Integer, String> sectionGroup = indexSectionGroups(sections, groups);
        if (!sectionGroup.isEmpty()) {
            symtab = tagGroups(symtab, sections, sectionGroup);
            dynsym = tagGroups(dynsym, sections, sectionGroup);
        }

        List<Relocation> relocations = parseRelocations(c, elf64, sections, symtab, dynsym);

        List<String> needed = new ArrayList<>();
        String soname = null;
        for (Section s : sections) {
            if (s.type() == Section.SHT_DYNAMIC) {
                long[] dyn = readDynamic(c, elf64, sections, s, needed);
                soname = dyn[1] < 0 ? null : readDynStr(c, sections, s, dyn[1]);
            }
        }

        return new ElfFile(name, elfClass, dataEncoding, machine, type,
                sections, symtab, dynsym, relocations, verDefs, verNeeds, groups,
                List.copyOf(needed), soname);
    }

    private static List<Section> nameSections(Cursor c, List<Section> sections, Section shstr) throws ElfException {
        List<Section> out = new ArrayList<>();
        for (Section s : sections) {
            String nm = "";
            if (s.type() != Section.SHT_NULL) {
                // name offset field lives at the same place on 32/64-bit: first word of the header.
                long base = s.index() == 0 ? 0 : findHeaderOffset(sections, s.index());
            }
            out.add(s);
        }
        return out;
    }

    private static long findHeaderOffset(List<Section> sections, int index) {
        return 0;
    }
