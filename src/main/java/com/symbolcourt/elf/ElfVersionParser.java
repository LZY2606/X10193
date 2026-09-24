package com.symbolcourt.elf;

import com.symbolcourt.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ElfVersionParser {

    private static final int VER_FLG_BASE = 0x1;
    private static final int VER_FLG_WEAK = 0x2;
    private static final int VERSYM_HIDDEN = 0x8000;
    private static final int VERSYM_VERSION = 0x7fff;

    void parseVersions(ByteReader r, ParsedElf elf, boolean elf64) {
        parseVerdef(r, elf);
        parseVerneed(r, elf);
        assignVersions(r, elf);
    }

    private ElfSection find(ParsedElf elf, int type) {
        for (ElfSection s : elf.sections) if (s.type == type) return s;
        return null;
    }

    private String safeName(ByteReader r, long nameOff, ElfSection strSec) {
        try {
            return r.cstringInSection(nameOff, strSec.offset, strSec.size);
        } catch (ElfFormatException e) {
            return "<bad-vername>";
        }
    }

    private void parseVerdef(ByteReader r, ParsedElf elf) {
        ElfSection sec = find(elf, ElfSection.SHT_GNU_VERDEF);
        if (sec == null || sec.data.length < 20) return;
        ElfSection strSec = elf.sectionAt(sec.link);
        if (strSec == null) throw new ElfFormatException("verdef string table missing");
        long off = 0;
        while (off >= 0 && off + 20 <= sec.data.length) {
            long base = sec.offset + off;
            int flags = r.u16(base + 4);
            int ndx = r.u16(base + 6);
            int cnt = r.u16(base + 8);
            long auxOff = r.u32(base + 12);
            long nextOff = r.u32(base + 16);
            VersionDef vd = new VersionDef();
            vd.index = ndx;
            vd.flags = flags;
            vd.base = (flags & VER_FLG_BASE) != 0;
            long v = auxOff == 0 ? -1 : off + auxOff;
            for (int i = 0; i < cnt && v >= 0 && v + 8 <= sec.data.length; i++) {
                long ab = sec.offset + v;
                long nameOff = r.u32(ab);
                long vnaNext = r.u32(ab + 4);
                String name = safeName(r, nameOff, strSec);
                if (i == 0) vd.name = name;
                else vd.aliases.add(name);
                v = vnaNext == 0 ? -1 : v + vnaNext;
            }
            if (vd.name == null) vd.name = "<unnamed>";
            elf.versionDefs.add(vd);
            off = nextOff == 0 ? -1 : off + nextOff;
        }
    }

    private void parseVerneed(ByteReader r, ParsedElf elf) {
        ElfSection sec = find(elf, ElfSection.SHT_GNU_VERNEED);
        if (sec == null || sec.data.length < 16) return;
        ElfSection strSec = elf.sectionAt(sec.link);
        if (strSec == null) throw new ElfFormatException("verneed string table missing");
        long off = 0;
        while (off >= 0 && off + 16 <= sec.data.length) {
            long base = sec.offset + off;
            int cnt = r.u16(base + 4);
            long fileOff = r.u32(base + 8);
            long auxOff = r.u32(base + 12);
            long nextOff = r.u32(base + 16);
            VersionNeed vn = new VersionNeed();
            vn.file = safeName(r, fileOff, strSec);
            long v = auxOff == 0 ? -1 : off + auxOff;
            for (int i = 0; i < cnt && v >= 0 && v + 16 <= sec.data.length; i++) {
                long ab = sec.offset + v;
                int flags = r.u16(ab + 6);
                long other = r.u32(ab + 8);
                long nameOff = r.u32(ab + 12);
                long vnaNext = r.u32(ab + 16);
                VersionNeed.Entry entry = new VersionNeed.Entry();
                entry.index = (int) other;
                entry.weak = (flags & VER_FLG_WEAK) != 0;
                entry.name = safeName(r, nameOff, strSec);
                vn.entries.add(entry);
                v = vnaNext == 0 ? -1 : v + vnaNext;
            }
            elf.versionNeeds.add(vn);
            off = nextOff == 0 ? -1 : off + nextOff;
        }
    }

    private void assignVersions(ByteReader r, ParsedElf elf) {
        ElfSection versym = find(elf, ElfSection.SHT_GNU_VERSYM);
        if (versym == null) return;
        Map<Integer, VersionDef> defByIndex = new HashMap<>();
        int firstNonBase = Integer.MAX_VALUE;
        VersionDef firstNonBaseDef = null;
        for (VersionDef vd : elf.versionDefs) {
 defByIndex.put(vd.index, vd);
            if (!vd.base && vd.index < firstNonBase) {
                firstNonBase = vd.index;
                firstNonBaseDef = vd;
            }
        }
        VersionDef baseDef = elf.versionDefs.stream().filter(d -> d.base)
                .findFirst().orElse(firstNonBaseDef);

        Map<Integer, VersionNeed.Entry> needByIndex = new HashMap<>();
        Map<Integer, String> needFile = new HashMap<>();
        for (VersionNeed vn : elf.versionNeeds) {
            for (VersionNeed.Entry e : vn.entries) {
                needByIndex.put(e.index, e);
                needFile.put(e.index, vn.file);
            }
        }

        long count = versym.data.length / 2L;
        assignTo(r, elf.dynamicSymbols, versym, count, defByIndex, needByIndex, needFile, baseDef);
    }

    private void assignTo(ByteReader r, List<ElfSymbol> symbols, ElfSection versym, long count,
                          Map<Integer, VersionDef> defByIndex,
                          Map<Integer, VersionNeed.Entry> needByIndex,
                          Map<Integer, String> needFile, VersionDef baseDef) {
        for (int i = 0; i < symbols.size() && i < count; i++) {
            ElfSymbol sym = symbols.get(i);
            int raw = r.u16(versym.offset + i * 2L);
            sym.rawVersionIndex = raw & VERSYM_VERSION;
            sym.versionHidden = (raw & VERSYM_HIDDEN) != 0;
            int idx = sym.rawVersionIndex;
            if (idx <= 1) {
                sym.versionName = null;
                sym.versionDefault = true;
                continue;
            }
            VersionDef vd = defByIndex.get(idx);
            if (vd != null) {
                sym.versionName = vd.name;
                sym.versionDefault = vd == baseDef;
                continue;
            }
            VersionNeed.Entry ne = needByIndex.get(idx);
            if (ne != null) {
                sym.versionName = ne.name;
                sym.versionDefault = false;
                sym.neededFrom = needFile.get(idx);
            }
        }
    }
}
