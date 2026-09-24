package com.symtribunal.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure-Java ELF parser. Supports ELF32/ELF64, little and big endian, symbol tables, dynamic
 * symbols, version definitions/requirements/versym, section groups (COMDAT) and relocations.
 * Any structural violation raises {@link ElfParseException}; callers isolate that one object.
 */
@Component
public class ElfParser {

    public ElfImage parse(byte[] data, String origin) {
        if (data == null || data.length < Elf.EI_NIDENT) {
            throw new ElfParseException("file too short for ELF identification");
        }
        if (data[0] != 0x7f || data[1] == 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ElfParseException("bad ELF magic");
        }
        int elfClass = data[Elf.EI_CLASS] & 0xff;
        if (elfClass != Elf.ELFCLASS32 && elfClass != Elf.ELFCLASS64) {
            throw new ElfParseException("unsupported ELF class " + elfClass);
        }
        int dataEncoding = data[Elf.EI_DATA] & 0xff;
        if (dataEncoding != Elf.ELFDATA2LSB && dataEncoding != Elf.ELFDATA2MSB) {
            throw new ElfParseException("unsupported ELF data encoding " + dataEncoding);
        }
        boolean elf64 = elfClass == Elf.ELFCLASS64;
        ByteReader reader = new ByteReader(data, dataEncoding == Elf.ELFDATA2LSB);

        ElfImage image = new ElfImage();
        image.origin = origin;
        image.bytes = data;
        image.elf64 = elf64;
        image.elfClass = elfClass;
        image.littleEndian = dataEncoding == Elf.ELFDATA2LSB;
        image.type = (int) reader.u16(16);
        image.machine = (int) reader.u16(18);
        image.entry = elf64 ? reader.u64(24) : reader.u32(24);
        image.programHeaderOffset = elf64 ? reader.u64(32) : reader.u32(28);
        image.sectionHeaderOffset = elf64 ? reader.u64(40) : reader.u32(32);
        image.flags = elf64 ? reader.u32(48) : reader.u32(36);
        image.sectionHeaderSize = elf64 ? (int) reader.u16(58) : (int) reader.u16(46);
        image.sectionCount = elf64 ? (int) reader.u16(60) : (int) reader.u16(48);
        image.sectionNameTableIndex = elf64 ? (int) reader.u16(62) : (int) reader.u16(50);

        parseSections(reader, image);
        if (image.sectionCount > 0) {
            if (image.sectionNameTableIndex >= image.sections.size()) {
                throw new ElfParseException("invalid section name table index "
                        + image.sectionNameTableIndex);
            }
            ElfSection shstrtab = image.sections.get(image.sectionNameTableIndex);
            if (shstrtab.type != Elf.SHT_STRTAB) {
                throw new ElfParseException("section name table is not SHT_STRTAB");
            }
            nameSections(reader, image, shstrtab);
        }

        parseExtendedSectionIndices(reader, image);
        parseDynamic(reader, image);
        ElfVersionParser.parse(reader, image);
        ElfGroupParser.mapMembers(reader, image);
        ElfSymbolParser.parse(reader, image);
        ElfGroupParser.resolveSignatures(image);
        ElfRelocationParser.parse(reader, image);
        return image;
    }

    private void parseSections(ByteReader reader, ElfImage image) {
        if (image.sectionHeaderSize == 0) {
            if (image.sectionCount != 0) {
                throw new ElfParseException("zero section header entry size");
            }
            image.sectionToGroup = new int[0];
            image.groupSymtabSection = new int[0];
            return;
        }
        int count = image.sectionCount;
        if (count == 0 && image.sectionHeaderOffset != 0) {
            count = (int) readSh(reader, image, 0, new ElfSection()).size;
            if (count < 0 || count > 65535) {
                throw new ElfParseException("implausible section count " + count);
            }
            image.sectionCount = count;
        }
        long offset = image.sectionHeaderOffset;
        int entrySize = image.sectionHeaderSize;
        int expectedMin = image.elf64 ? 64 : 40;
        if (entrySize < expectedMin) {
            throw new ElfParseException("section header entry too small: " + entrySize);
        }
        reader.require(offset, (long) count * entrySize);
        for (int i = 0; i < count; i++) {
            ElfSection section = new ElfSection();
            section.index = i;
            readSh(reader, image, offset + (long) i * entrySize, section);
            validateSectionRange(reader, section);
            image.sections.add(section);
        }
        if (count == 0) {
            image.sectionCount = 0;
        }
        image.sectionToGroup = new int[image.sections.size()];
        image.groupSymtabSection = new int[image.sections.size()];
        java.util.Arrays.fill(image.sectionToGroup, -1);
        java.util.Arrays.fill(image.groupSymtabSection, -1);
    }

    private ElfSection readSh(ByteReader reader, ElfImage image, long base, ElfSection section) {
        if (image.elf64) {
            section.name = "";
            int nameOffset = (int) reader.u32(base);
            section.type = (int) reader.u32(base + 4);
            section.flags = reader.u64(base + 8);
            section.address = reader.u64(base + 16);
            section.offset = reader.u64(base + 24);
            section.size = reader.u64(base + 32);
            section.link = (int) reader.u32(base + 40);
            section.info = (int) reader.u32(base + 44);
            section.addralign = reader.u64(base + 48);
            section.entsize = reader.u64(base + 56);
            section.nameOffset = nameOffset;
        } else {
            int nameOffset = (int) reader.u32(base);
            section.type = (int) reader.u32(base + 4);
            section.flags = reader.u32(base + 8);
            section.address = reader.u32(base + 12);
            section.offset = reader.u32(base + 16);
            section.size = reader.u32(base + 20);
            section.link = (int) reader.u32(base + 24);
            section.info = (int) reader.u32(base + 28);
            section.addralign = reader.u32(base + 32);
            section.entsize = reader.u32(base + 36);
            section.nameOffset = nameOffset;
        }
        return section;
    }

    private void validateSectionRange(ByteReader reader, ElfSection section) {
        if (section.type == Elf.SHT_NOBITS) {
            return;
        }
        if (section.size > 0) {
            reader.require(section.offset, section.size);
        }
    }

    private void nameSections(ByteReader reader, ElfImage image, ElfSection shstrtab) {
        for (ElfSection section : image.sections) {
            if (section.index == 0) {
                continue;
            }
            section.name = reader.cString(shstrtab.offset + section.nameOffset);
        }
    }

    /** SHT_SYMTAB_SHNDX: extended section indices for symbol tables. */
    private void parseExtendedSectionIndices(ByteReader reader, ElfImage image) {
        image.symSectionIndices = new int[image.sections.size()][];
        for (ElfSection section : image.sections) {
            if (section.type != Elf.SHT_SYMTAB_SHNDX) {
                continue;
            }
            if (section.link <= 0 || section.link >= image.sections.size()) {
                throw new ElfParseException("SHT_SYMTAB_SHNDX bad link " + section.link);
            }
            long count = section.size / 4;
            int[] indices = new int[(int) count];
            for (int i = 0; i < count; i++) {
                indices[i] = (int) reader.u32(section.offset + (long) i * 4);
            }
            image.symSectionIndices[section.link] = indices;
        }
    }

    private void parseDynamic(ByteReader reader, ElfImage image) {
        for (ElfSection section : image.sections) {
            if (section.type != Elf.SHT_DYNAMIC) {
                continue;
            }
            int ent = image.elf64 ? 16 : 8;
            long count = section.size / ent;
            if (section.size % ent != 0) {
                throw new ElfParseException("SHT_DYNAMIC size not a multiple of entry size");
            }
            ElfSection dynstr = findDynStr(image, section);
            Map<Long, Long> values = new HashMap<>();
            for (int i = 0; i < count; i++) {
                long base = section.offset + (long) i * ent;
                long tag = image.elf64 ? reader.u64(base) : reader.u32(base);
                long val = image.elf64 ? reader.u64(base + 8) : reader.u32(base + 4);
                values.merge(tag, val, (a, b) -> a);
                if (tag == Elf.DT_NEEDED && dynstr != null) {
                    image.needed.add(reader.cString(dynstr.offset + val));
                } else if (tag == Elf.DT_SONAME && dynstr != null) {
                    image.soname = reader.cString(dynstr.offset + val);
                } else if (tag == Elf.DT_NULL) {
                    break;
                }
            }
            if (dynstr == null && values.containsKey(Elf.DT_NEEDED)) {
                throw new ElfParseException("SHT_DYNAMIC has DT_NEEDED but no linked string table");
            }
        }
    }

    private ElfSection findDynStr(ElfImage image, ElfSection dynamic) {
        if (dynamic.link <= 0 || dynamic.link >= image.sections.size()) {
            return null;
        }
        ElfSection linked = image.sections.get(dynamic.link);
        return linked.type == Elf.SHT_STRTAB ? linked : null;
    }
}
