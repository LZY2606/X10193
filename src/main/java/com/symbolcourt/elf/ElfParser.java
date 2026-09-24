package com.symbolcourt.elf;

import com.symbolcourt.model.*;
import com.symbolcourt.model.Enums.*;

import java.util.ArrayList;
import java.util.List;

public class ElfParser {

    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;
    private static final int ELFCLASS32 = 1;
    private static final int ELFCLASS64 = 2;
    private static final int ELFDATA2LSB = 1;
    private static final int ELFDATA2MSB = 2;

    private static final int ET_REL = 1;
    private static final int ET_EXEC = 2;
    private static final int ET_DYN = 3;

    private static final int EM_386 = 3;
    private static final int EM_ARM = 40;
    private static final int EM_X86_64 = 62;
    private static final int EM_AARCH64 = 183;
    private static final int EM_RISCV = 243;

    private static final int PT_LOAD = 1;
    private static final int PT_INTERP = 3;

    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_SONAME = 14;
    private static final long DT_STRTAB = 5;
    private static final long DT_STRSZ = 10;
    private static final long DT_VERNEEDNUM = 0x6fffffff - 2;

    public ParsedElf parse(byte[] data, String origin, String displayName) {
        ParsedElf elf = new ParsedElf();
        elf.origin = origin;
        elf.displayName = displayName;
        try {
            parseOrThrow(data, elf);
        } catch (ElfFormatException e) {
            elf.corrupt = true;
            elf.error = e.getMessage();
        } catch (RuntimeException e) {
            elf.corrupt = true;
            elf.error = "malformed ELF: " + e;
        }
        return elf;
    }

    private void parseOrThrow(byte[] data, ParsedElf elf) {
        if (data == null || data.length < 16
                || data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfFormatException("bad ELF magic");
        }
        int cls = data[EI_CLASS] & 0xff;
        if (cls == ELFCLASS32) elf.elfClass = ElfClass.ELF32;
        else if (cls == ELFCLASS64) elf.elfClass = ElfClass.ELF64;
        else throw new ElfFormatException("unknown ELF class: " + cls);

        int enc = data[EI_DATA] & 0xff;
        if (enc == ELFDATA2LSB) elf.endian = Endian.LITTLE;
        else if (enc == ELFDATA2MSB) elf.endian = Endian.BIG;
        else throw new ElfFormatException("unknown data encoding: " + enc);

        ByteReader r = new ByteReader(data, elf.endian);
        boolean elf64 = elf.elfClass == ElfClass.ELF64;

        int type = r.u16(16);
        int machine = r.u16(18);
        elf.machineValue = machine;
        elf.machine = mapMachine(machine);
        elf.kind = switch (type) {
            case ET_REL -> ObjectKind.RELOCATABLE;
            case ET_EXEC -> ObjectKind.EXECUTABLE;
            case ET_DYN -> ObjectKind.SHARED;
            default -> ObjectKind.UNKNOWN;
        };

        int phentsize = r.u16(elf64 ? 54 : 42);
        int phnum = r.u16(elf64 ? 56 : 44);
        int phoff = elf64 ? (int) r.u64(32) : (int) r.u32(28);
        if (phnum > 0) readProgramHeaders(r, elf, phoff, phentsize, phnum, elf64);

        long shoff = elf64 ? r.u64(40) : r.u32(32);
        int shentsize = r.u16(elf64 ? 58 : 46);
        int shnum = r.u16(elf64 ? 60 : 48);
        int shstrndx = r.u16(elf64 ? 62 : 50);
        if (shnum == 0) {
            throw new ElfFormatException("no section headers");
        }
        if (shoff <= 0 || shentsize <= 0) {
            throw new ElfFormatException("invalid section header table");
        }
        readSections(r, elf, shoff, shentsize, shnum, shstrndx, elf64);
        readDynamic(r, elf, elf64);
        new ElfSymbolParser().parseSymbols(r, elf, elf64);
        new ElfVersionParser().parseVersions(r, elf, elf64);
        new ElfGroupParser().parseGroups(r, elf, elf64);
        new ElfRelocParser().parseRelocations(r, elf, elf64);
    }

    private Machine mapMachine(int m) {
        return switch (m) {
            case EM_X86_64, EM_386 -> Machine.X86_64;
            case EM_AARCH64, EM_ARM, EM_RISCV -> Machine.AARCH64;
            default -> Machine.GENERIC;
        };
    }

    private void readProgramHeaders(ByteReader r, ParsedElf elf, long phoff, int entsize, int num,
                                    boolean elf64) {
        if (entsize < (elf64 ? 56 : 32)) {
            throw new ElfFormatException("program header entry too small");
        }
        long need = phoff + (long) num * entsize;
        if (need < 0 || need > r.data.length) {
            throw new ElfFormatException("program headers exceed file");
        }
        for (int i = 0; i < num; i++) {
            long off = phoff + (long) i * entsize;
            int type = (int) r.u32(off);
            if (type == PT_INTERP) {
                long fileOff = elf64 ? r.u64(off + 8) : r.u32(off + 4);
                long fileSz = elf64 ? r.u64(off + 32) : r.u32(off + 16);
                if (fileSz > 0 && fileSz < 4096 && r.inRange(fileOff, (int) Math.min(fileSz, r.data.length - fileOff))) {
                    int len = (int) Math.min(fileSz, r.data.length - fileOff);
                    while (len > 0 && r.data[(int) fileOff + len - 1] == 0) len--;
                    elf.interpreter = new String(r.data, (int) fileOff, len);
                }
            }
        }
    }

    private void readSections(ByteReader r, ParsedElf elf, long shoff, int entsize, int num,
                              int shstrndx, boolean elf64) {
        int minEnt = elf64 ? 64 : 40;
        if (entsize < minEnt) {
            throw new ElfFormatException("section header entry too small");
        }
        long need = shoff + (long) num * entsize;
        if (need < 0 || need > r.data.length) {
            throw new ElfFormatException("section headers exceed file");
        }
        List<ElfSection> sections = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            long off = shoff + (long) i * entsize;
            ElfSection s = new ElfSection();
            s.index = i;
            s.name = "";
            if (elf64) {
                s.type = (int) r.u32(off);
                s.flags = r.u64(off + 8);
                s.addr = r.u64(off + 16);
                s.offset = r.u64(off + 24);
                s.size = r.u64(off + 32);
                s.link = (int) r.u32(off + 40);
                s.info = (int) r.u32(off + 44);
                s.addralign = r.u64(off + 48);
                s.entsize = r.u64(off + 56);
                int nameIdx = (int) r.u32(off + 4);
                s.nameOffset = nameIdx;
            } else {
                s.type = (int) r.u32(off + 4);
                s.flags = r.u32(off + 8);
                s.addr = r.u32(off + 12);
                s.offset = r.u32(off + 16);
                s.size = r.u32(off + 20);
                s.link = (int) r.u32(off + 24);
                s.info = (int) r.u32(off + 28);
                s.addralign = r.u32(off + 32);
                s.entsize = r.u32(off + 36);
                s.nameOffset = (int) r.u32(off);
            }
            if (s.size < 0 || s.size > ByteReader.MAX_SANE) {
                throw new ElfFormatException("section size invalid at index " + i);
            }
            if (s.type != 8 /* SHT_NOBITS */ && s.size > 0) {
                if (s.offset < 0 || s.offset + s.size > r.data.length || s.offset + s.size < 0) {
                    throw new ElfFormatException("section data out of file at index " + i);
                }
                s.data = r.slice(s.offset, s.size);
            } else {
                s.data = new byte[0];
            }
            sections.add(s);
        }
        if (shstrndx > 0 && shstrndx < sections.size()) {
            ElfSection shstr = sections.get(shstrndx);
            for (ElfSection s : sections) {
                try {
                    s.name = r.cstringInSection(s.nameOffset, shstr.offset, shstr.size);
                } catch (ElfFormatException e) {
                    s.name = "<bad-name>";
                }
            }
        }
        elf.sections = sections;
    }

    private void readDynamic(ByteReader r, ParsedElf elf, boolean elf64) {
        ElfSection dyn = findSection(elf, ElfSection.SHT_DYNAMIC, ".dynamic");
        if (dyn == null) return;
        int ent = elf64 ? 16 : 8;
        long strAddr = -1;
        long strSize = 64;
        List<Integer> neededIdx = new ArrayList<>();
        List<Integer> sonameIdx = new ArrayList<>();
        for (long off = 0; off + ent <= dyn.data.length; off += ent) {
            long tag = elf64 ? r.u64(dyn.offset + off) : r.u32(dyn.offset + off);
            long val = elf64 ? r.u64(dyn.offset + off + 8) : r.u32(dyn.offset + off + 4);
            if (tag == DT_NULL) break;
            if (tag == DT_NEEDED) neededIdx.add((int) val);
            else if (tag == DT_SONAME) sonameIdx.add((int) val);
            else if (tag == DT_STRTAB) strAddr = val;
            else if (tag == DT_STRSZ) strSize = val;
        }
        ElfSection strSec = findSectionByAddr(elf, strAddr);
        long strBase = strSec != null ? strSec.offset : (strAddr >= 0 && strAddr < r.data.length ? strAddr : -1);
        long strLen = strSec != null ? strSec.size : strSize;
        if (strBase >= 0) {
            for (int idx : neededIdx) {
                try {
                    elf.needed.add(r.cstringInSection(idx, strBase, strLen));
                } catch (ElfFormatException e) {
                    elf.needed.add("<bad-needed>");
                }
            }
            if (!sonameIdx.isEmpty()) {
                try {
                    elf.soname = r.cstringInSection(sonameIdx.get(0), strBase, strLen);
                } catch (ElfFormatException ignored) {
                }
            }
        } else {
            for (int i = 0; i < neededIdx.size(); i++) elf.needed.add("<no-dynstr>");
        }
    }

    static ElfSection findSection(ParsedElf elf, int type, String name) {
        for (ElfSection s : elf.sections) {
            if (s.type == type && (name == null || s.name.equals(name))) return s;
        }
        return null;
    }

    static ElfSection findSectionByAddr(ParsedElf elf, long addr) {
        if (addr < 0) return null;
        for (ElfSection s : elf.sections) {
            if ((s.flags & ElfSection.SHF_ALLOC) != 0 && s.size > 0
                    && addr >= s.addr && addr < s.addr + s.size) {
                return s;
            }
        }
        return null;
    }
}
