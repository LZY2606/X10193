package com.tribunal.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tribunal.elf.ElfConstants.*;

/**
 * Self-contained ELF parser used by the symbol tribunal. It never invokes the
 * system linker/binutils. Supports 32- and 64-bit, little- and big-endian.
 * All offsets and indices are validated; corrupted input raises
 * ElfFormatException and is isolated to the offending object.
 */
public final class ElfParser {

    public ParsedElf parse(byte[] data, String objectName) {
        if (data == null || data.length < 16) {
            throw new ElfFormatException("file too short for ELF identification");
        }
        if ((data[0] & 0xff) != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfFormatException("bad ELF magic");
        }
        int elfClass = data[4] & 0xff;
        if (elfClass != 1 && elfClass != 2) {
            throw new ElfFormatException("unsupported EI_CLASS: " + elfClass);
        }
        int dataEnc = data[5] & 0xff;
        if (dataEnc != 1 && dataEnc != 2) {
            throw new ElfFormatException("unsupported EI_DATA: " + dataEnc);
        }
        boolean le = dataEnc == 1;
        ByteReader r = new ByteReader(data, le);
        if (r.u8(6) != 1) {
            throw new ElfFormatException("unsupported ELF version (EI_VERSION)");
        }
        boolean is64 = elfClass == 2;

        int ehSize = is64 ? 64 : 52;
        if (data.length < ehSize) {
            throw new ElfFormatException("file shorter than ELF header");
        }
        int type = r.u16(16);
        int machine = r.u16(18);
        long shoff = is64 ? r.u64(40) : r.u32(32);
        int shentsize = r.u16(is64 ? 58 : 46);
        int shnum = r.u16(is64 ? 60 : 48);
        int shstrndx = r.u16(is64 ? 62 : 50);
        int expectedShent = is64 ? 64 : 40;
        if (shentsize < expectedShent) {
            throw new ElfFormatException("unexpected e_shentsize=" + shentsize);
        }
        if (shnum == 0 && shoff != 0) {
            // Extended section numbering: real count lives in section 0's sh_size.
            if ((long) shoff + shentsize > data.length) {
                throw new ElfFormatException("extended shdr table out of range");
            }
            shnum = (int) (is64 ? r.u64((int) shoff + 32) : r.u32((int) shoff + 20));
            if (shstrndx == 0xffff) {
                shstrndx = (int) (is64 ? r.u64((int) shoff + 40) : r.u32((int) shoff + 24));
            }
        }
        if (shoff > Integer.MAX_VALUE || shnum < 0 || (long) shoff + (long) shnum * shentsize > data.length) {
            throw new ElfFormatException("section header table out of file bounds");
        }
        if (shnum > 0 && (shstrndx < 0 || shstrndx >= shnum)) {
            throw new ElfFormatException("e_shstrndx out of range: " + shstrndx);
        }

        List<ElfSection> sections = readSections(r, (int) shoff, shentsize, shnum, is64, shstrndx);
        ParsedElf partial = new ParsedElf(objectName, elfClass, machine, type, type == ET_DYN,
                sections, List.of(), List.of(), Map.of(), List.of(), List.of(), null);
        ParseState st = new ParseState(partial, r, is64);
        return st.run();
    }

    private List<ElfSection> readSections(ByteReader r, int shoff, int entsize, int num,
                                          boolean is64, int shstrndx) {
        List<ElfSection> raw = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            int o = shoff + i * entsize;
            int nameOff = r.u16(o);
            int type = (int) r.u32(o + 4);
            long flags = is64 ? r.u64(o + 8) : r.u32(o + 8);
            long addr = is64 ? r.u64(o + 16) : r.u32(o + 12);
            long offset = is64 ? r.u64(o + 24) : r.u32(o + 16);
            long size = is64 ? r.u64(o + 32) : r.u32(o + 20);
            int link = (int) r.u32(o + (is64 ? 40 : 24));
            int info = (int) r.u32(o + (is64 ? 44 : 28));
            long align = is64 ? r.u64(o + 48) : r.u32(o + 32);
            long entSize = is64 ? r.u64(o + 56) : r.u32(o + 36);
            if (type != SHT_NOBITS && offset + size > r.size()) {
                throw new ElfFormatException("section " + i + " data out of bounds (type=" + type + ")");
            }
            raw.add(new ElfSection(i, Integer.toString(nameOff), type, flags, addr, offset,
                    size, link, info, align, entSize));
        }
        String[] names = new String[num];
        if (num > 0) {
            ElfSection str = raw.get(shstrndx);
            names = new String[num];
            for (int i = 0; i < num; i++) {
                int nameOff = Integer.parseInt(raw.get(i).name);
                long base = str.offset;
                long abs = base + nameOff;
                if (abs < 0 || abs > Integer.MAX_VALUE) {
                    names[i] = "";
                } else {
                    try {
                        names[i] = r.cString((int) abs);
                    } catch (ElfFormatException ex) {
                        names[i] = "";
                    }
                }
            }
        }
        List<ElfSection> named = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            ElfSection s = raw.get(i);
            named.add(new ElfSection(i, names[i], s.type, s.flags, s.address, s.offset, s.size,
                    s.link, s.info, s.alignment, s.entrySize));
        }
        return named;
    }
}
