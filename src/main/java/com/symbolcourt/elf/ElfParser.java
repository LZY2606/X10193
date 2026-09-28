package com.symbolcourt.elf;

import com.symbolcourt.model.ElfModels.ElfObject;
import com.symbolcourt.model.ElfModels.ElfRelocation;
import com.symbolcourt.model.ElfModels.ElfSymbol;
import com.symbolcourt.model.ElfModels.SectionGroup;
import com.symbolcourt.model.ElfModels.SectionInfo;
import com.symbolcourt.model.ElfModels.VersionNode;
import com.symbolcourt.model.Enums;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.symbolcourt.elf.ByteReader.*;

public class ElfParser {

    public static class ParseOutcome {
        public ElfObject object;
        public String error;
    }

    public ParseOutcome parse(String label, String binaryId, byte[] data) {
        ParseOutcome outcome = new ParseOutcome();
        try {
            outcome.object = doParse(label, binaryId, null, null, data);
        } catch (ByteReader.ElfFormatException e) {
            outcome.error = e.getMessage();
        } catch (RuntimeException e) {
            outcome.error = "解析失败: " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        return outcome;
    }

    public ParseOutcome parseMember(String binaryId, String archiveName, String memberName, byte[] data) {
        ParseOutcome outcome = new ParseOutcome();
        try {
            outcome.object = doParse(memberName, binaryId, archiveName, memberName, data);
        } catch (ByteReader.ElfFormatException e) {
            outcome.error = e.getMessage();
        } catch (RuntimeException e) {
            outcome.error = "解析失败: " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        return outcome;
    }

    private ElfObject doParse(String label, String binaryId, String archiveName, String memberName, byte[] data) {
        if (data.length < 16) {
            throw new ByteReader.ElfFormatException("文件长度不足 ELF 头");
        }
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            throw new ByteReader.ElfFormatException("缺少 ELF magic");
        }
        int eiClass = data[4] & 0xff;
        int eiData = data[5] & 0xff;
        if (eiClass != 1 && eiClass != 2) {
            throw new ByteReader.ElfFormatException("未知 EI_CLASS=" + eiClass);
        }
        if (eiData != 1 && eiData != 2) {
            throw new ByteReader.ElfFormatException("未知 EI_DATA=" + eiData);
        }
        Enums.ElfClass elfClass = eiClass == 2 ? Enums.ElfClass.ELF64 : Enums.ElfClass.ELF32;
        Enums.DataEncoding encoding = eiData == 1 ? Enums.DataEncoding.LSB : Enums.DataEncoding.MSB;
        ByteReader r = new ByteReader(data, eiData == 2);
        boolean is64 = eiClass == 2;

        long type = r.u16(16);
        long machine = r.u16(18);
        long e_shoff = is64 ? r.u64(0x28) : r.u32(0x20);
        long e_shentsize = is64 ? r.u16(0x3a) : r.u16(0x2e);
        long e_shnum = is64 ? r.u16(0x3c) : r.u16(0x30);
        long e_shstrndx = is64 ? r.u16(0x3e) : r.u16(0x32);

        Enums.ObjectKind kind = switch ((int) type) {
            case 1 -> Enums.ObjectKind.RELOCATABLE;
            case 2 -> Enums.ObjectKind.EXECUTABLE;
            case 3 -> Enums.ObjectKind.SHARED;
            case 4 -> Enums.ObjectKind.CORE;
            default -> Enums.ObjectKind.UNKNOWN;
        };
        Enums.MachineType machineType = switch ((int) machine) {
            case 62 -> Enums.MachineType.X86_64;
            case 3 -> Enums.MachineType.I386;
            default -> Enums.MachineType.OTHER;
        };

        if (e_shoff == 0 || e_shnum == 0) {
            throw new ByteReader.ElfFormatException("无 section header table（stripped 对象暂不支持）");
        }
        int shEntSize = (int) e_shentsize;
        int expected = is64 ? 64 : 40;
        if (shEntSize < expected) {
            throw new ByteReader.ElfFormatException("shentsize 过小: " + shEntSize);
        }
        long tableBytes = e_shnum * (long) shEntSize;
        r.require(e_shoff, tableBytes);

        int shnum = (int) e_shnum;
        int shstrndx = (int) e_shstrndx;
        long[] shOffset = new long[shnum];
        long[] shSize = new long[shnum];
        int[] shType = new int[shnum];
        int[] shLink = new int[shnum];
        int[] shInfo = new int[shnum];
        long[] shFlags = new long[shnum];
        long[] shAddr = new long[shnum];
        long[] shAddralign = new long[shnum];
        int[] shNameIdx = new int[shnum];

        int realShnum = shnum;
        int realShstrndx = shstrndx;
        if (shnum == 0) {
            realShnum = (int) (is64 ? r.u64(e_shoff + 32) : r.u32(e_shoff + 20));
        }
        if (shstrndx == 0xffff) {
            realShstrndx = (int) (is64 ? r.u64(e_shoff + 40) : r.u32(e_shoff + 24));
        }

        for (int i = 0; i < shnum; i++) {
            long base = e_shoff + i * (long) shEntSize;
            shNameIdx[i] = (int) r.u32(base);
            shType[i] = (int) r.u32(base + 4);
            shFlags[i] = is64 ? r.u64(base + 8) : r.u32(base + 8);
            shAddr[i] = is64 ? r.u64(base + 16) : r.u32(base + 12);
            shOffset[i] = is64 ? r.u64(base + 24) : r.u32(base + 16);
            shSize[i] = is64 ? r.u64(base + 32) : r.u32(base + 20);
            shLink[i] = (int) (is64 ? r.u32(base + 40) : r.u32(base + 24));
            shInfo[i] = (int) (is64 ? r.u32(base + 44) : r.u32(base + 28));
            shAddralign[i] = is64 ? r.u64(base + 48) : r.u32(base + 32);
            if (shType[i] != SHT_NOBITS && shSize[i] > 0) {
                r.require(shOffset[i], shSize[i]);
            }
        }

        String[] sectionNames = new String[shnum];
        if (realShstrndx > 0 && realShstrndx < shnum) {
            long strOff = shOffset[realShstrndx];
            long strSize = shSize[realShstrndx];
            for (int i = 0; i < shnum; i++) {
                sectionNames[i] = r.stringFromTable(strOff, strSize, shNameIdx[i]);
            }
        } else {
            for (int i = 0; i < shnum; i++) {
                sectionNames[i] = "";
            }
        }

        List<SectionInfo> sections = new ArrayList<>();
        for (int i = 0; i < shnum; i++) {
            sections.add(new SectionInfo(i, sectionNames[i], shType[i], shFlags[i],
                    shAddr[i], shSize[i], shLink[i], shInfo[i], shAddralign[i]));
        }

        List<ElfSymbol> symbols = new ArrayList<>();
        List<ElfRelocation> relocations = new ArrayList<>();
        List<VersionNode> versionNodes = new ArrayList<>();
        List<SectionGroup> groups = new ArrayList<>();
        String soname = null;
        List<String> needed = new ArrayList<>();

        Map<Integer, int[]> groupSections = new HashMap<>();
        for (int i = 0; i < shnum; i++) {
            if (shType[i] == SHT_GROUP) {
                long entsize = is64 ? 4 : 4;
                long count = shSize[i] / entsize;
                if (count > 1_000_000) {
                    throw new ByteReader.ElfFormatException("group 成员数异常");
                }
                int[] members = new int[(int) Math.max(0, count - 1)];
                for (int j = 1; j < count; j++) {
                    members[j - 1] = (int) r.u32(shOffset[i] + j * entsize);
                }
                groupSections.put(i, members);
            }
        }

        Map<Integer, Integer> sectionToGroup = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : groupSections.entrySet()) {
            for (int s : e.getValue()) {
                if (s >= 0 && s < shnum) {
                    sectionToGroup.put(s, e.getKey());
                }
            }
        }

        int symtabIdx = -1;
        int dynsymIdx = -1;
        for (int i = 0; i < shnum; i++) {
            if (shType[i] == SHT_SYMTAB && symtabIdx == -1) {
                symtabIdx = i;
            }
            if (shType[i] == SHT_DYNSYM && dynsymIdx == -1) {
                dynsymIdx = i;
            }
        }

        Set<Integer> discardedSections = new HashSet<>();
        List<ElfSymbol> symtabSymbols = symtabIdx >= 0
                ? parseSymbolTable(r, is64, symtabIdx, shOffset, shSize, shLink, shType,
                    sectionNames, sectionToGroup, discardedSections, groupSections, groups)
                : List.of();
        List<ElfSymbol> dynsymSymbols = dynsymIdx >= 0
                ? parseSymbolTable(r, is64, dynsymIdx, shOffset, shSize, shLink, shType,
                    sectionNames, sectionToGroup, discardedSections, groupSections, groups)
                : List.of();

        Map<Integer, ElfSymbol> symtabByIndex = new HashMap<>();
        for (ElfSymbol s : symtabSymbols) {
            symtabByIndex.put(s.index(), s);
        }

        resolveGroups(r, groupSections, symtabByIndex, shOffset, shSize, shLink, groups,
                label, discardedSections);

        symtabSymbols = markDiscarded(symtabSymbols, discardedSections, shNameOf(symtabSymbols));
        dynsymSymbols = markDiscarded(dynsymSymbols, discardedSections, null);

        int verdefIdx = findSection(shType, SHT_GNU_VERDEF);
        int verneedIdx = findSection(shType, SHT_GNU_VERNEED);
        int versymSymtab = findVersymFor(shType, symtabIdx);
        int versymDynsym = findVersymFor(shType, dynsymIdx);

        Map<Integer, VersionNode> verNodesByIndex = new HashMap<>();
        if (verdefIdx >= 0) {
            parseVerdef(r, verdefIdx, shOffset, shSize, shLink, verNodesByIndex, versionNodes);
        }
        if (verneedIdx >= 0) {
            parseVerneed(r, verneedIdx, shOffset, shSize, shLink, verNodesByIndex, versionNodes);
        }

        if (versymSymtab >= 0) {
            applyVersym(r, versymSymtab, shOffset, shSize, verNodesByIndex, symtabSymbols);
        }
        if (versymDynsym >= 0) {
            applyVersym(r, versymDynsym, shOffset, shSize, verNodesByIndex, dynsymSymbols);
        }

        Map<Integer, ElfSymbol> mergedByKey = new HashMap<>();
        for (ElfSymbol s : symtabSymbols) {
            mergedByKey.put(key(0, s.index()), s);
        }
        List<ElfSymbol> allSymbols = new ArrayList<>(symtabSymbols);
        for (ElfSymbol s : dynsymSymbols) {
            allSymbols.add(s);
        }
        symbols.addAll(allSymbols);

        for (int i = 0; i < shnum; i++) {
            if (shType[i] == SHT_RELA || shType[i] == SHT_REL) {
                parseRelocations(r, is64, i, shType, shOffset, shSize, shLink, shInfo,
                        symtabIdx, dynsymIdx, symtabSymbols, dynsymSymbols,
                        sectionNames, relocations, machineType);
            }
            if (shType[i] == SHT_DYNAMIC) {
                parseDynamic(r, is64, i, shOffset, shSize, shLink, needed,
                        str -> soname = str);
            }
        }

        byte[] hash = sha256(data);
        return new ElfObject(label, binaryId, archiveName, memberName, hash,
                elfClass, encoding, machineType, kind, soname, needed,
                sections, symbols, relocations, versionNodes, groups, null);
    }

    private int[] shNameOf(List<ElfSymbol> unused) {
        return new int[0];
    }

    private static int key(int table, int index) {
        return table * 1_000_000 + index;
    }

    private int findSection(int[] types, int wanted) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == wanted) {
                return i;
            }
        }
        return -1;
    }

    private int findVersymFor(int[] types, int symIdx) {
        if (symIdx < 0) {
            return -1;
        }
        for (int i = 0; i < types.length; i++) {
            if (types[i] == SHT_GNU_VERSYM) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
