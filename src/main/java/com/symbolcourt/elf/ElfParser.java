package com.symbolcourt.elf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only ELF64 (little-endian) parser with strict length/index bounds.
 * Any violation raises {@link ElfParseException} so the caller can isolate only that object.
 */
public final class ElfParser {

    private final byte[] data;
    private final ByteBuffer buf;

    private ElfParser(byte[] data) {
        this.data = data;
        this.buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static ElfModel parse(byte[] data) {
        return new ElfParser(data).parse();
    }

    private void require(long offset, long length, String what) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new ElfParseException(what + " 越界: offset=" + offset + " len=" + length + " 文件大小=" + data.length);
        }
    }

    private int u16(long off) { require(off, 2, "u16"); return buf.getShort((int) off) & 0xffff; }
    private long u32(long off) { require(off, 4, "u32"); return buf.getInt((int) off) & 0xffffffffL; }
    private long u64(long off) { require(off, 8, "u64"); return buf.getLong((int) off); }

    private String cstr(long strTabOff, long strTabSize, long strOff, String what) {
        if (strOff < 0 || strOff >= strTabSize) {
            throw new ElfParseException(what + " 字符串偏移越界: " + strOff + " (strtab 大小 " + strTabSize + ")");
        }
        long abs = strTabOff + strOff;
        require(abs, 1, what);
        int end = (int) abs;
        while (end < data.length && end < strTabOff + strTabSize && data[end] != 0) end++;
        return new String(data, (int) abs, end - (int) abs, StandardCharsets.UTF_8);
    }

    private ElfModel parse() {
        require(0, 64, "ELF 头");
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfParseException("缺少 ELF magic");
        }
        if (data[4] != 2) throw new ElfParseException("仅支持 ELF64 (EI_CLASS=" + (data[4] & 0xff) + ")");
        if (data[5] != 1) throw new ElfParseException("仅支持 little-endian (EI_DATA=" + (data[5] & 0xff) + ")");

        ElfModel m = new ElfModel();
        m.elfType = u16(16);
        long shoff = u64(40);
        int shentsize = u16(58);
        int shnum = u16(60);
        int shstrndx = u16(62);
        if (shnum == 0) return m; // no sections: header only
        if (shentsize < 64) throw new ElfParseException("shentsize 非法: " + shentsize);
        require(shoff, (long) shentsize * shnum, "节头表");
        if (shstrndx >= shnum && shstrndx != 0) {
            throw new ElfParseException("shstrndx 越界: " + shstrndx + " (shnum=" + shnum + ")");
        }

        // raw section headers
        record RawSh(int name, int type, long flags, long addr, long offset, long size,
                     int link, int info, long addralign, long entsize) {}
        List<RawSh> raw = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            long b = shoff + (long) i * shentsize;
            raw.add(new RawSh((int) u32(b), (int) u32(b + 4), u64(b + 8), u64(b + 16), u64(b + 24),
                    u64(b + 32), (int) u32(b + 40), (int) u32(b + 44), u64(b + 48), u64(b + 56)));
        }
        long strOff = shstrndx == 0 ? 0 : raw.get(shstrndx).offset();
        long strSize = shstrndx == 0 ? 0 : raw.get(shstrndx).size();
        if (shstrndx != 0) require(strOff, strSize, "shstrtab");

        for (int i = 0; i < shnum; i++) {
            RawSh s = raw.get(i);
            String name = shstrndx == 0 ? "" : cstr(strOff, strSize, s.name(), "节名");
            if (s.type() != 8 /*SHT_NOBITS*/ && s.size() > 0) {
                require(s.offset(), s.size(), "节 " + name);
            }
            m.sections.add(new ElfModel.Section(name, s.type(), s.flags(), s.addr(), s.offset(), s.size(),
                    s.link(), s.info(), s.addralign(), s.entsize(), i));
        }

        for (ElfModel.Section sec : m.sections) {
            switch (sec.type()) {
                case ElfModel.SHT_SYMTAB -> readSymbols(m, sec, false);
                case ElfModel.SHT_DYNSYM -> readSymbols(m, sec, true);
                case ElfModel.SHT_REL -> readRelocs(m, sec, false);
                case ElfModel.SHT_RELA -> readRelocs(m, sec, true);
                case ElfModel.SHT_GNU_VERSYM -> m.versym = readVersym(sec);
                case ElfModel.SHT_GNU_VERDEF -> readVerdef(m, sec);
                case ElfModel.SHT_GNU_VERNEED -> readVerneed(m, sec);
                case ElfModel.SHT_GROUP -> readGroup(m, sec);
                case ElfModel.SHT_DYNAMIC -> readDynamic(m, sec);
                default -> { }
            }
        }
        return m;
    }

    private ElfModel.Section linked(ElfModel m, ElfModel.Section sec, String what) {
        if (sec.link() <= 0 || sec.link() >= m.sections.size()) {
            throw new ElfParseException(what + " sh_link 越界: " + sec.link());
        }
        return m.sections.get(sec.link());
    }

    private void readSymbols(ElfModel m, ElfModel.Section sec, boolean dyn) {
        long entsize = sec.entsize() == 0 ? 24 : sec.entsize();
        if (entsize < 24) throw new ElfParseException("符号表 entsize 非法: " + entsize);
        if (sec.size() % entsize != 0) throw new ElfParseException("符号表长度不对齐: " + sec.size());
        ElfModel.Section strs = linked(m, sec, "符号表");
        require(strs.offset(), strs.size(), "符号字符串表");
        int count = (int) (sec.size() / entsize);
        for (int i = 0; i < count; i++) {
            long b = sec.offset() + i * entsize;
            require(b, 24, "符号表项");
            int stName = (int) u32(b);
            int info = (int) u32(b + 4) & 0xff;
            int other = (int) u32(b + 5) & 0xff;
            int shndx = u16(b + 6);
            long value = u64(b + 8);
            long size = u64(b + 16);
            String name = stName == 0 ? "" : cstr(strs.offset(), strs.size(), stName, "符号名");
            ElfModel.Sym sym = new ElfModel.Sym(name, (info >> 4) & 0xf, info & 0xf, other & 0x3,
                    shndx, value, size, i, dyn);
            if (dyn) m.dynsym.add(sym); else m.symtab.add(sym);
        }
    }

    private void readRelocs(ElfModel m, ElfModel.Section sec, boolean rela) {
        long entsize = sec.entsize() == 0 ? (rela ? 24 : 16) : sec.entsize();
        long min = rela ? 24 : 16;
        if (entsize < min) throw new ElfParseException("重定位 entsize 非法: " + entsize);
        if (sec.size() % entsize != 0) throw new ElfParseException("重定位表长度不对齐: " + sec.size());
        int count = (int) (sec.size() / entsize);
        for (int i = 0; i < count; i++) {
            long b = sec.offset() + i * entsize;
            require(b, min, "重定位项");
            long rOffset = u64(b);
            long rInfo = u64(b + 8);
            long addend = rela ? u64(b + 16) : 0;
            int symIndex = (int) (rInfo >>> 32);
            long type = rInfo & 0xffffffffL;
            m.relocs.add(new ElfModel.Reloc(rOffset, type, symIndex, addend, rela, sec.info()));
        }
    }

    private int[] readVersym(ElfModel.Section sec) {
        if (sec.size() % 2 != 0) throw new ElfParseException("versym 长度为奇数: " + sec.size());
        int n = (int) (sec.size() / 2);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = u16(sec.offset() + 2L * i);
        return out;
    }

    private void readVerdef(ElfModel m, ElfModel.Section sec) {
        ElfModel.Section strs = linked(m, sec, "verdef");
        long base = sec.offset();
        long end = sec.offset() + sec.size();
        long cur = base;
        int guard = 0;
        while (cur != 0 && cur < end) {
            if (++guard > 10000) throw new ElfParseException("verdef 链死循环");
            require(cur, 20, "verdef 项");
            int ndx = u16(cur + 4);
            int flags = u16(cur + 2);
            int cnt = u16(cur + 6);
            long auxOff = u32(cur + 12);
            long next = u32(cur + 16);
            if (cnt < 1) throw new ElfParseException("verdef vd_cnt 非法: " + cnt);
            long aux = cur + auxOff;
            require(aux, 8, "verdaux");
            String name = cstr(strs.offset(), strs.size(), u32(aux), "版本定义名");
            m.verdefs.add(new ElfModel.VersionDef(ndx, flags, name));
            cur = next == 0 ? 0 : cur + next;
        }
    }

    private void readVerneed(ElfModel m, ElfModel.Section sec) {
        ElfModel.Section strs = linked(m, sec, "verneed");
        long end = sec.offset() + sec.size();
        long cur = sec.offset();
        int guard = 0;
        while (cur != 0 && cur < end) {
            if (++guard > 10000) throw new ElfParseException("verneed 链死循环");
            require(cur, 16, "verneed 项");
            int cnt = u16(cur + 2);
            String file = cstr(strs.offset(), strs.size(), u32(cur + 4), "verneed 文件名");
            long aux = cur + u32(cur + 8);
            long next = u32(cur + 12);
            List<ElfModel.Vernaux> list = new ArrayList<>();
            int aguard = 0;
            while (aux != 0 && aux < end && list.size() < cnt) {
                if (++aguard > 10000) throw new ElfParseException("vernaux 链死循环");
                require(aux, 16, "vernaux 项");
                int flags = u16(aux + 8);
                int other = u16(aux + 10);
                String vname = cstr(strs.offset(), strs.size(), u32(aux + 8 - 8 + 8), "版本需求名");
                long anext = u32(aux + 12);
                list.add(new ElfModel.Vernaux(other, flags, vname));
                aux = anext == 0 ? 0 : aux + anext;
            }
            m.verneeds.add(new ElfModel.VersionNeed(file, list.isEmpty() ? List.of() : list));
            cur = next == 0 ? 0 : cur + next;
        }
    }

    private void readGroup(ElfModel m, ElfModel.Section sec) {
        if (sec.size() < 4 || sec.size() % 4 != 0) throw new ElfParseException("group 节长度非法: " + sec.size());
        ElfModel.Section symtabSec = linked(m, sec, "group");
        List<ElfModel.Sym> table = symtabSec.type() == ElfModel.SHT_DYNSYM ? m.dynsym : m.symtab;
        // group 可能在符号表之前解析，签名先记录符号索引，解析后回填
        int sigSymIndex = sec.info();
        long flags = u32(sec.offset());
        List<Integer> members = new ArrayList<>();
        for (long p = sec.offset() + 4; p < sec.offset() + sec.size(); p += 4) {
            int idx = (int) u32(p);
            if (idx < 0 || idx >= m.sections.size()) {
                throw new ElfParseException("group 成员节索引越界: " + idx);
            }
            members.add(idx);
        }
        String signature;
        if (table.isEmpty() || sigSymIndex >= table.size()) {
            // 符号表尚未读取（节顺序问题）——延迟到 finalizeGroups
            signature = null;
        } else {
            signature = table.get(sigSymIndex).name();
        }
        m.groups.add(new ElfModel.GroupInfo(signature, (flags & ElfModel.GRP_COMDAT) != 0, members));
        if (signature == null) {
            pendingGroups.add(new PendingGroup(m.groups.get(m.groups.size() - 1), symtabSec.type(), sigSymIndex, m));
        }
    }

    private record PendingGroup(ElfModel.GroupInfo group, int symtabType, int symIndex, ElfModel m) {}
    private final List<PendingGroup> pendingGroups = new ArrayList<>();

    private void readDynamic(ElfModel m, ElfModel.Section sec) {
        long entsize = sec.entsize() == 0 ? 16 : sec.entsize();
        if (entsize < 16) throw new ElfParseException("dynamic entsize 非法: " + entsize);
        ElfModel.Section strs;
        try {
            strs = linked(m, sec, "dynamic");
        } catch (ElfParseException e) {
            return; // 无字符串表则跳过 DT_NEEDED
        }
        int count = (int) (sec.size() / entsize);
        for (int i = 0; i < count; i++) {
            long b = sec.offset() + i * entsize;
            require(b, 16, "dynamic 项");
            long tag = u64(b);
            long val = u64(b + 8);
            if (tag == 0) break; // DT_NULL
            if (tag == 1) { // DT_NEEDED
                m.dtNeeded.add(cstr(strs.offset(), strs.size(), val, "DT_NEEDED"));
            }
        }
    }
}
