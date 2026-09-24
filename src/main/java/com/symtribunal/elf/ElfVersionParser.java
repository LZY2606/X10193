package com.symtribunal.elf;

import java.util.HashMap;
import java.util.Map;

/** Parses SHT_GNU_verdef / verneed chains and maps versym indices to version names. */
final class ElfVersionParser {
    private ElfVersionParser() {}

    static void parse(ByteReader reader, ElfImage image) {
        Map<Integer, String> indexToName = new HashMap<>();
        Map<Integer, Boolean> indexHidden = new HashMap<>();

        for (ElfSection section : image.sections) {
            if (section.type == Elf.SHT_GNU_verdef) {
                parseVerdef(reader, image, section, indexToName, indexHidden);
            } else if (section.type == Elf.SHT_GNU_verneed) {
                parseVerneed(reader, image, section, indexToName, indexHidden);
            }
        }

        for (ElfSection section : image.sections) {
            if (section.type != Elf.SHT_GNU_versym) {
                continue;
            }
            if (section.link <= 0 || section.link >= image.sections.size()) {
                throw new ElfParseException("versym bad link " + section.link);
            }
            ElfSection dynsym = image.sections.get(section.link);
            if (dynsym.type != Elf.SHT_DYNSYM && dynsym.type != Elf.SHT_SYMTAB) {
                throw new ElfParseException("versym linked section is not a symbol table");
            }
            long count = dynsym.size / (image.elf64 ? 24 : 16);
            if (section.size < count * 2) {
                throw new ElfParseException("versym shorter than dynsym");
            }
            image.dynVersions = new java.util.ArrayList<>((int) count);
            image.dynVersionHidden = new boolean[(int) count];
            for (int i = 0; i < count; i++) {
                int raw = reader.u16(section.offset + (long) i * 2);
                int idx = raw & Elf.VERSYM_VERSION;
                boolean hidden = (raw & Elf.VERSYM_HIDDEN) != 0;
                String name = indexToName.get(idx);
                image.dynVersions.add(name);
                image.dynVersionHidden[i] = hidden || Boolean.TRUE.equals(indexHidden.get(idx));
            }
        }
    }

    private static void parseVerdef(ByteReader reader, ElfImage image, ElfSection section,
                                    Map<Integer, String> indexToName,
                                    Map<Integer, Boolean> indexHidden) {
        long cursor = section.offset;
        long end = section.offset + section.size;
        int guard = 0;
        while (cursor < end) {
            if (guard++ > 4096) {
                throw new ElfParseException("verdef chain too long");
            }
            long vdVersion = reader.u32(cursor);
            long vdFlags = reader.u32(cursor + 4);
            long vdNdx = reader.u16(cursor + 8);
            long vdCnt = reader.u16(cursor + 10);
            long vdHash = reader.u32(cursor + 12);
            long vdAux = reader.u32(cursor + 16);
            long vdNext = reader.u32(cursor + 20);
            if (vdVersion == 0 && vdNext == 0) {
                break;
            }
            if (vdVersion != 1 || vdCnt < 1 || vdAux == 0) {
                throw new ElfParseException("invalid verdef entry");
            }
            if (vdNdx < 0 || vdNdx > 0xfffe) {
                throw new ElfParseException("invalid verdef index " + vdNdx);
            }
            ElfVersionDef def = new ElfVersionDef();
            def.index = (int) vdNdx;
            def.flags = vdFlags;
            long auxCursor = cursor + vdAux;
            for (int i = 0; i < vdCnt; i++) {
                if (auxCursor + 8 > end) {
                    throw new ElfParseException("verdef aux out of bounds");
                }
                long vdaName = reader.u32(auxCursor);
                long vdaNext = reader.u32(auxCursor + 4);
                if (vdaName == 0 || cursor + vdaName > end) {
                    throw new ElfParseException("verdef aux name out of bounds");
                }
                String name = reader.cString(cursor + vdaName);
                if (i == 0) {
                    def.name = name;
                    indexToName.put((int) vdNdx, name);
                    indexHidden.put((int) vdNdx, (vdFlags & Elf.VERSYM_HIDDEN) != 0);
                } else {
                    def.aliases.add(name);
                }
                if (vdaNext == 0) {
                    break;
                }
                auxCursor += vdaNext;
            }
            image.versionDefs.add(def);
            if (vdNext == 0) {
                break;
            }
            cursor += vdNext;
        }
    }

    private static void parseVerneed(ByteReader reader, ElfImage image, ElfSection section,
                                     Map<Integer, String> indexToName,
                                     Map<Integer, Boolean> indexHidden) {
        long end = section.offset + section.size;
        long cursor = section.offset;
        int guard = 0;
        while (cursor < end) {
            if (guard++ > 4096) {
                throw new ElfParseException("verneed chain too long");
            }
            long vnVersion = reader.u16(cursor);
            long vnCnt = reader.u16(cursor + 2);
            long vnFile = reader.u32(cursor + 4);
            long vnAux = reader.u32(cursor + 8);
            long vnNext = reader.u32(cursor + 12);
            if (vnVersion == 0 && vnNext == 0) {
                break;
            }
            if (vnVersion != 1 || vnCnt < 1 || vnAux == 0 || vnFile == 0
                    || cursor + vnFile > end) {
                throw new ElfParseException("invalid verneed entry");
            }
            ElfVersionNeed need = new ElfVersionNeed();
            need.file = reader.cString(cursor + vnFile);
            long auxCursor = cursor + vnAux;
            int auxGuard = 0;
            for (long i = 0; i < vnCnt; i++) {
                if (auxGuard++ > 4096 || auxCursor + 16 > end) {
                    throw new ElfParseException("vernaux out of bounds");
                }
                long vnaHash = reader.u32(auxCursor);
                long vnaFlags = reader.u16(auxCursor + 4);
                long vnaOther = reader.u16(auxCursor + 6);
                long vnaName = reader.u32(auxCursor + 8);
                long vnaNext = reader.u32(auxCursor + 12);
                if (vnaName == 0 || cursor + vnaName > end || vnaOther < 2 || vnaOther > 0xfffe) {
                    throw new ElfParseException("invalid vernaux entry");
                }
                ElfVersionNeed.Entry entry = new ElfVersionNeed.Entry();
                entry.name = reader.cString(cursor + vnaName);
                entry.flags = vnaFlags;
                entry.other = (int) vnaOther;
                entry.weak = (vnaFlags & Elf.VER_FLG_WEAK) != 0;
                need.entries.add(entry);
                indexToName.put((int) vnaOther, entry.name);
                indexHidden.put((int) vnaOther, (vnaFlags & Elf.VERSYM_HIDDEN) != 0);
                if (vnaNext == 0) {
                    break;
                }
                auxCursor += vnaNext;
            }
            image.versionNeeds.add(need);
            if (vnNext == 0) {
                break;
            }
            cursor += vnNext;
        }
    }
}
