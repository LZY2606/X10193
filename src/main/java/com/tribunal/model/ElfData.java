package com.tribunal.model;

import java.util.List;
import java.util.Map;

public record ElfData(
        String objectName,
        String archiveName,
        String memberName,
        boolean elf64,
        boolean littleEndian,
        int type,
        int machine,
        int flags,
        List<ElfSection> sections,
        List<ElfSymbol> symbols,
        List<ElfRelocation> relocations,
        Map<Integer, VersionNode> versions,
        Map<Integer, Integer> symbolVersions,
        List<String> needed,
        List<String> dependencies,
        List<GroupInfo> groups,
        List<ParseNotice> notices
) {
    public ElfData withName(String archive, String member) {
        String name = archive == null ? member : archive + "(" + member + ")";
        return new ElfData(name, archive, member, elf64, littleEndian, type, machine, flags,
                sections, symbols, relocations, versions, symbolVersions, needed, dependencies,
                groups, notices);
    }
}
