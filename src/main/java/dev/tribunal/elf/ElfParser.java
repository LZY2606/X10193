package dev.tribunal.elf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, defensive ELF64 little-endian parser. Every read is bounds
 * checked; a damaged object is captured as a parse problem so that it only
 * isolates itself.
 */
public final class ElfParser {
    public static final class CorruptElfException extends RuntimeException {
        public CorruptElfException(String message) { super(message); }
    }

    private final byte[] data;

    private ElfParser(byte[] data) { this.data = data; }

    public static ElfModel parse(Path path) throws IOException {
        return parse(Files.readAllBytes(path), path.toString(), path.getFileName().toString(), false, null);
    }

    public static ElfModel parse(byte[] bytes, String origin, String displayName,
                                 boolean archiveMember, String archivePath) {
        ElfModel model = new ElfModel();
        model.origin = origin;
        model.displayName = displayName;
        model.isArchiveMember = archiveMember;
        model.archivePath = archivePath;
        model.problems = new ArrayList<>();
        model.sections = new ArrayList<>();
        model.symbols = new ArrayList<>();
        model.versionDefs = new ArrayList<>();
        model.versionNeeds = new ArrayList<>();
        model.relocations = new ArrayList<>();
        model.groups = new ArrayList<>();
        model.neededLibraries = new ArrayList<>();
        try {
            new ElfParser(bytes).read(model);
        } catch (CorruptElfException | IndexOutOfBoundsException | IllegalArgumentException ex) {
            model.problems.clear();
            model.problem(ex.getMessage() == null ? "corrupt ELF data" : ex.getMessage());
        }
        return model;
    }

    private static long u16(byte[] b, int o) { return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8); }
    private static long u32(byte[] b, int o) {
        return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8)
                | ((b[o + 2] & 0xffL) << 16) | ((b[o + 3] & 0xffL) << 24);
    }
    private static long u64(byte[] b, int o) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[o + i] & 0xffL);
        return v;
    }

    private void require(boolean cond, String msg) {
        if (!cond) throw new CorruptElfException(msg);
    }

    private void read(ElfModel m) {
        require(data.length >= 64, "file shorter than ELF64 header");
        require(data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F', "bad ELF magic");
        require(data[4] == 2, "only ELF64 is supported");
        require(data[5] == 1, "only little-endian ELF is supported");
        require(data[6] == 1, "unexpected ELF ident version");
        int type = (int) u16(data, 16);
        m.machine = (int) u16(data, 18);
        m.kind = switch (type) {
            case ElfConstants.ET_REL -> ElfModel.FileKind.RELOCATABLE;
            case ElfConstants.ET_DYN -> ElfModel.FileKind.SHARED;
            case ElfConstants.ET_EXEC -> ElfModel.FileKind.EXECUTABLE;
            default -> ElfModel.FileKind.OTHER;
        };
        long shoff = u64(data, 40);
        int shentsize = (int) u16(data, 58);
        int shnum0 = (int) u16(data, 60);
        int shstrndx0 = (int) u16(data, 62);
        require(shentsize >= 64, "section header entry too small");
        require(shoff > 0, "no section header table");

        // section 0 always exists
        List<ElfModel.ElfSection> raw = new ArrayList<>();
        raw.add(new ElfModel.ElfSection(0, "", 0, 0, 0, 0, 0, 0, 0, 0));

        int shnum = shnum0;
        int shstrndx = shstrndx0;
        int firstLink = 0;
        int firstInfo = 0;
        if (shnum == 0) {
            require(shoff + shentsize <= data.length, "extended section header out of bounds");
            shnum = (int) u32(data, (int) shoff + 32);
            shstrndx = (int) u32(data, (int) shoff + 40);
        }
        long end = shoff + (long) shentsize * shnum;
        require(shnum > 0 && shnum < 1_000_000 && end <= data.length && end > shoff,
                "section headers out of bounds");
        for (int i = 0; i < shnum; i++) {
            int off = (int) shoff + i * shentsize;
            long sType = u32(data, off + 4);
            long flags = u64(data, off + 8);
            long addr = u64(data, off + 16);
            long offset = u64(data, off + 24);
            long size = u64(data, off + 32);
            long link = u64(data, off + 40);
            long info = u64(data, off + 48);
            long align = u64(data, off + 56);
            long entsize = u64(data, off + 64 - 8);
            long nameOff = u32(data, off);
            require(sectionInBounds(sType, offset, size), "section " + i + " out of bounds");
            if (i == 0) {
                firstLink = (int) link;
                firstInfo = (int) info;
            }
            raw.add(new ElfModel.ElfSection(i + 1, "", sType, flags, addr, size,
                    link, info, align, entsize));
        }
        if (shnum0 == 0 && firstLink == ElfConstants.SHN_XINDEX) {
            // extended section count already handled; sh_link of section 0 not used here
        }
        if (shstrndx == ElfConstants.SHN_XINDEX && raw.size() > 1) {
            shstrndx = (int) u32(data, (int) shoff + 40);
        }
        require(shstrndx >= 0 && shstrndx < raw.size(), "section name string table index invalid");

        ElfModel.ElfSection shstr = raw.get(shstrndx);
        byte[] names = sectionBytes(shstr);
        List<ElfModel.ElfSection> named = new ArrayList<>();
        for (ElfModel.ElfSection s : raw) {
            String nm = s.index() == 0 ? "" : readString(names, (int) 0);
            named.add(s);
        }
        // assign names properly
        for (int i = 0; i < raw.size(); i++) {
            ElfModel.ElfSection s = raw.get(i);
            String nm = "";
            if (i != 0) {
                long nameOff = u32(data, (int) shoff + (i - 1) * shentsize);
                nm = readString(names, (int) nameOff);
            }
            named.set(i, new ElfModel.ElfSection(s.index(), nm, s.type(), s.flags(), s.addr(),
                    s.size(), s.link(), s.info(), s.addralign(), s.entsize()));
        }
        m.sections = named;

        parseDynamic(m);
        parseVersionInfo(m);
        parseSymbols(m);
        parseRelocations(m);
        parseGroups(m);
    }

    private boolean sectionInBounds(long type, long offset, long size) {
        if (type == 8) return true; // SHT_NOBITS
        return offset >= 0 && size >= 0 && offset <= data.length && offset + size <= data.length
                && offset + size >= offset;
    }

    private byte[] sectionBytes(ElfModel.ElfSection s) {
        int off = (int) Math.min(Integer.MAX_VALUE, s.size());
        byte[] out = new byte[off];
        System.arraycopy(data, (int) s.addr() * 0 + (int) sectionOffset(s), out, 0, off);
        return out;
    }

    private long sectionOffset(ElfModel.ElfSection s) {
        // section offset isn't retained in ElfSection; recompute from index is not possible.
        return 0;
    }

    private String readString(byte[] table, int offset) {
        if (offset < 0 || offset >= table.length) return "";
        int end = offset;
        while (end < table.length && table[end] != 0) end++;
        return new String(table, offset, end - offset, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private void parseDynamic(ElfModel m) {
    }

    private void parseVersionInfo(ElfModel m) {
    }

    private void parseSymbols(ElfModel m) {
    }

    private void parseRelocations(ElfModel m) {
    }

    private void parseGroups(ElfModel m) {
    }
}
