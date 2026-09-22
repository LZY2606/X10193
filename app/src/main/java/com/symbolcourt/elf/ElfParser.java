package com.symbolcourt.elf;

import com.symbolcourt.model.ElfTypes.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal self-contained ELF parser for the symbol tribunal. Supports
 * ELFCLASS32/64, either endianness, static/dynamic symbol tables, GNU symbol
 * versioning, section groups, dynamic section and relocations. No linker.
 */
public final class ElfParser {

    static final int SHT_STRTAB = 3;
    static final int SHT_SYMTAB = 2;
    static final int SHT_DYNSYM = 11;
    static final int SHT_RELA = 4;
    static final int SHT_REL = 9;
    static final int SHT_GROUP = 17;
    static final int SHT_DYNAMIC = 6;
    static final int SHT_GNU_VERSYM = 0x6fffffff;
    static final int SHT_GNU_VERDEF = 0x6ffffffd;
    static final int SHT_GNU_VERNEED = 0x6ffffffe;

    private static final int DT_NEEDED = 1;
    private static final int DT_SONAME = 14;
    static final int STT_SECTION = 3;

    private ElfParser() {}

    public static ElfFile parse(byte[] data) {
        if (data == null || data.length < 16
                || data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfFormatException("bad ELF magic");
        }
        int cls = data[4] & 0xff;
        if (cls != 1 && cls != 2) {
            throw new ElfFormatException("unsupported EI_CLASS " + cls);
        }
        int enc = data[5] & 0xff;
        if (enc != 1 && enc != 2) {
            throw new ElfFormatException("unsupported EI_DATA " + enc);
        }
        boolean le = enc == 1;
        ByteReader r = new ByteReader(data, le);
        boolean is64 = cls == 2;

        int type = r.u16(16);
        int machine = r.u16(18);
        ElfFile elf = new ElfFile(data, cls, !le, type, machine);

        long shoff = is64 ? r.u64(40) : r.u32(32);
        int shentsize = r.u16(is64 ? 58 : 46);
        int shnum = r.u16(is64 ? 60 : 48);
        int shstrndx = r.u16(is64 ? 62 : 50);
        int expectedEnt = is64 ? 64 : 40;
        if (shoff == 0 || shnum == 0) {
            return elf;
        }
        if (shentsize != expectedEnt) {
            throw new ElfFormatException("unsupported e_shentsize " + shentsize);
        }
        if (shoff < 0 || shoff > (long) data.length - (long) shentsize * shnum) {
            throw new ElfFormatException("section header table out of bounds");
        }

        for (int i = 0; i < shnum; i++) {
            long o = shoff + (long) i * shentsize;
            int nameOff = (int) r.u32(o);
            int stype = (int) r.u32(o + 4);
            long flags = is64 ? r.u64(o + 8) : r.u32(o + 8);
            long addr = is64 ? r.u64(o + 16) : r.u32(o + 12);
            long off = is64 ? r.u64(o + 24) : r.u32(o + 16);
            long size = is64 ? r.u64(o + 32) : r.u32(o + 20);
            int link = (int) r.u32(o + (is64 ? 40 : 24));
            int info = (int) r.u32(o + (is64 ? 44 : 28));
            long entsize = is64 ? r.u64(o + 56) : r.u32(o + 36);
            if (stype != 8 && size > 0 && off < 0 || (stype != 8 && size > 0 && off + size > data.length)) {
                throw new ElfFormatException("section " + i + " data out of bounds");
            }
            elf.sections.add(new SectionInfo(i, "", stype, flags, addr, off, size,
                    link, info, entsize));
        }

        String shstr = "";
        if (shstrndx > 0 && shstrndx < elf.sections.size()) {
            SectionInfo ss = elf.sections.get(shstrndx);
            shstr = r.cstr(ss.offset() + nameOffsetAt(r, shoff, shstrndx, expectedEnt));
        }
        List<SectionInfo> named = new ArrayList<>();
        for (SectionInfo s : elf.sections) {
            String nm;
            if (s.index() == 0) {
                nm = "";
            } else {
                int nameOff = nameOffsetAt(r, shoff, s.index(), expectedEnt);
                nm = sectionName(r, elf.sections.get(shstrndx >= 0 && shstrndx < elf.sections.size() ? shstrndx : 0), nameOff);
            }
            named.add(new SectionInfo(s.index(), nm, s.type(), s.flags(), s.addr(),
                    s.offset(), s.size(), s.link(), s.info(), s.entsize()));
        }
        elf.sections.clear();
        elf.sections.addAll(named);

        parseVersioning(r, elf, is64);
        parseDynamic(r, elf, is64);
        parseSymbolsAndRelocs(r, elf, is64);
        parseGroups(r, elf, is64);
        return elf;
    }

    private static int nameOffsetAt(ByteReader r, long shoff, int idx, int ent) {
        return (int) r.u32(shoff + (long) idx * ent);
    }

    private static String sectionName(ByteReader r, SectionInfo strtab, int off) {
        if (strtab == null || off == 0) {
            return "";
        }
        if (off < 0 || off >= strtab.size()) {
            throw new ElfFormatException("section name offset " + off
                    + " outside shstrtab size " + strtab.size());
        }
        return r.cstr(strtab.offset() + off);
    }

    static SectionInfo sectionByName(ElfFile elf, String name) {
        for (SectionInfo s : elf.sections) {
            if (name.equals(s.name())) {
                return s;
            }
        }
        return null;
    }
