package com.tribunal.elf;

import com.tribunal.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.tribunal.elf.ElfConstants.*;

@Component
public class ElfParser {
    public ElfData parse(byte[] b, String name) throws ElfParseException {
        if (b == null || b.length < 52 || b[0] != 0x7f || b[1] != 'E' || b[2] != 'L' || b[3] != 'F') {
            throw new ElfParseException("not an ELF object");
        }
        boolean elf64 = b[4] == 2;
        boolean le = b[5] == 1;
        if ((b[4] & 255) != 1 && !elf64 || (b[5] & 255) != 1 && (b[5] & 255) != 2) {
            throw new ElfParseException("unsupported ELF class or byte order");
        }
        ByteReader r = new ByteReader(b, le);
        int type = r.u16(16);
        int machine = r.u16(18);
        int flags = (int) r.u32(36);
        long shoff = elf64 ? r.u64(40) : r.u32(32);
        int shentsize = r.u16(elf64 ? 58 : 46);
        int shnum = r.u16(elf64 ? 60 : 48);
        int shstrndx = r.u16(elf64 ? 62 : 50);
        if (shnum == 0 && shoff != 0) {
            if (shoff + shentsize > b.length) throw new ElfParseException("extended section header is truncated");
            shnum = (int) (elf64 ? new ByteReader(b, le).u64(shoff + 32) : new ByteReader(b, le).u32(shoff + 20));
            if (shstrndx == SHN_XINDEX) shstrndx = (int) (elf64 ? new ByteReader(b, le).u32(shoff + 40) : new ByteReader(b, le).u32(shoff + 24));
        }
        validateTable(shoff, shentsize, shnum, b.length, "section headers");

        List<RawSection> raw = readSections(r, elf64, shoff, shnum, shentsize);
        List<ElfSection> sections = nameSections(r, raw, shstrndx);
        Map<String, Long> dynStr = readDynamicStringTable(r, sections);
        List<String> needed = readNeeded(r, sections, dynStr);
        List<ParseNotice> notices = new ArrayList<>();
        Map<Integer, VersionNode> versions = new LinkedHashMap<>();
        versions.put(0, new VersionNode(0, "*local*", false, false, null, true, false, name));
        versions.put(1, new VersionNode(1, "*global*", false, false, null, true, false, name));
        readVerdef(r, sections, versions, notices);
        readVerneed(r, sections, dynStr, versions, notices);
        Map<Integer, Integer> symbolVersions = new HashMap<>();
        List<ElfSymbol> symbols = new ArrayList<>();
        for (ElfSection s : sections) {
            if (s.type() == SHT_SYMTAB || s.type() == SHT_DYNSYM) symbols.addAll(readSymbols(r, s, sections, versions));
        }
        applyVersym(r, sections, symbols, symbolVersions, versions);
        List<ElfRelocation> rels = readRelocations(r, sections, symbols, machine);
        List<GroupInfo> groups = readGroups(r, sections);
        return new ElfData(name, null, name, elf64, le, type, machine, flags, sections, symbols, rels,
                versions, symbolVersions, needed, new ArrayList<>(needed), groups, notices);
    }

    private void validateTable(long off, long ent, long count, int len, String what) throws ElfParseException {
        if (count < 0 || ent <= 0 || off < 0 || off > len || (long) count * ent > len - off) {
            throw new ElfParseException(what + " are outside the object");
        }
    }

    private List<RawSection> readSections(ByteReader r, boolean e64, long off, int count, int ent) throws ElfParseException {
        List<RawSection> list = new ArrayList<>();
        list.add(new RawSection(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        for (int i = 1; i < count; i++) {
            long p = off + (long) i * ent;
            list.add(e64 ? new RawSection(i, r.u32(p), r.u32(p+4), r.u64(p+8), r.u64(p+16),
                    r.u64(p+24), r.u64(p+32), r.u32(p+40), r.u32(p+44), r.u64(p+48), r.u64(p+56))
                    : new RawSection(i, r.u32(p), r.u32(p+4), r.u32(p+8), r.u32(p+12),
                    r.u32(p+16), r.u32(p+20), r.u32(p+24), r.u32(p+28), r.u32(p+32), r.u32(p+36)));
        }
        return list;
    }

    private List<ElfSection> nameSections(ByteReader r, List<RawSection> raw, int strndx) throws ElfParseException {
        if (strndx <= 0 || strndx >= raw.size()) return raw.stream().map(RawSection::toElf).toList();
        RawSection str = raw.get(strndx);
        List<ElfSection> out = new ArrayList<>();
        for (RawSection s : raw) {
            String n = "";
            if (s.index != 0) {
                try { n = r.cString(str.offset + s.nameOffset, str.size); }
                catch (ElfParseException e) { n = "<invalid-name>"; }
            }
            out.add(new ElfSection(s.index, n, s.type, s.flags, s.address, s.offset, s.size, s.link, s.info, s.align, s.entsize));
        }
        return out;
    }

    private record RawSection(int index, long nameOffset, int type, long flags, long address, long offset,
                              long size, int link, int info, long align, long entsize) {
        ElfSection toElf() {
            return new ElfSection(index, "", type, flags, address, offset, size, link, info, align, entsize);
        }
    }

    private List<ElfSymbol> readSymbols(ByteReader r, ElfSection st, List<ElfSection> sections,
                                        Map<Integer, VersionNode> versions) throws ElfParseException {
        if (st.link() <= 0 || st.link() >= sections.size()) throw new ElfParseException("symbol string table link is invalid");
        ElfSection str = sections.get(st.link());
        int ent = (int) (st.entrySize() > 0 ? st.entrySize() : st.type() == SHT_DYNSYM ? 24 : 24);
        long count = st.size() / ent;
        List<ElfSymbol> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long p = st.offset() + (long) i * ent;
            int nameOff, bind, type, vis, shndx;
            long value, size;
            if (st.entrySize() >= 24 || ent >= 24) {
                nameOff = (int) r.u32(p);
                int info = r.u8(p + 4);
                vis = r.u8(p + 5) & 3;
                shndx = r.u16(p + 6);
                value = r.u64(p + 8);
                size = r.u64(p + 16);
                bind = info >> 4;
                type = info & 15;
            } else {
                nameOff = (int) r.u32(p);
                value = r.u32(p + 4);
                size = r.u32(p + 8);
                int info = r.u8(p + 12);
                vis = r.u8(p + 13) & 3;
                shndx = r.u16(p + 14);
                bind = info >> 4;
                type = info & 15;
            }
            String name;
            try { name = r.cString(str.offset() + nameOff, str.size()); }
            catch (ElfParseException e) { name = "<invalid-symbol-name>"; }
            VersionNode v = versions.get(1);
            out.add(new ElfSymbol(i, name, value, size, bind, type, vis, shndx, shndx != SHN_UNDEF,
                    shndx == SHN_COMMON, 1, v.name(), false, false));
        }
        return out;
    }
}
