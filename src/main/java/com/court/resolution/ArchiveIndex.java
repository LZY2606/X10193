package com.court.resolution;

import com.court.elf.ArchiveParser;
import com.court.elf.ElfParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds one archive's members in deterministic order and the symbol table
 * mapping "undefined name -> member that defines it".
 *
 * The archive symbol table uses big-endian member header offsets. We resolve
 * those to our member map by re-scanning headers, and never rely on
 * filesystem/directory iteration order.
 */
public class ArchiveIndex {
    public static class Entry {
        public final String memberName;
        public final ArchiveParser.ArchiveMember member;
        public boolean extracted;

        public Entry(String memberName, ArchiveParser.ArchiveMember member) {
            this.memberName = memberName;
            this.member = member;
        }
    }

    private final String label;
    private final int loadOrder;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, List<Integer>> symbolToEntries = new LinkedHashMap<>();

    public ArchiveIndex(String label, int loadOrder, byte[] data) throws ElfParseException {
        this.label = label;
        this.loadOrder = loadOrder;
        Map<String, ArchiveParser.ArchiveMember> members = ArchiveParser.parse(data);
        for (Map.Entry<String, ArchiveParser.ArchiveMember> e : members.entrySet()) {
            entries.add(new Entry(e.getKey(), e.getValue()));
        }
        Map<String, Integer> table = ArchiveParser.symbolTable(data);
        // Symbol table ordinals count only real members; align them by scanning order.
        List<String> realNames = new ArrayList<>();
        for (Entry en : entries) realNames.add(en.memberName);
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            int ord = e.getValue();
            if (ord < 0 || ord >= realNames.size()) continue;
            String target = realNames.get(ord);
            int idx = indexOf(target);
            if (idx >= 0) {
                symbolToEntries.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                List<Integer> list = symbolToEntries.get(e.getKey());
                if (!list.contains(idx)) list.add(idx);
            }
        }
    }

    private int indexOf(String memberName) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).memberName.equals(memberName)) return i;
        }
        return -1;
    }

    public String getLabel() { return label; }
    public int getLoadOrder() { return loadOrder; }
    public List<Entry> getEntries() { return entries; }

    /** Candidates (by member index) advertised for a name in the archive symbol table. */
    public List<Integer> providersFor(String name) {
        return symbolToEntries.getOrDefault(name, List.of());
    }

    public Entry entryAt(int idx) { return entries.get(idx); }
}
