package com.symbolcourt.engine;

import com.symbolcourt.elf.ArchiveMember;
import com.symbolcourt.elf.ArchiveReader;
import com.symbolcourt.elf.ElfFile;
import com.symbolcourt.elf.ElfReader;

import java.util.ArrayList;
import java.util.List;

public class InputLoader {

    public static final class LoadedInput {
        public final List<InputItem> items = new ArrayList<>();
        public final List<Diagnostic> diagnostics = new ArrayList<>();
    }

    public LoadedInput load(List<StoredInput> inputs) {
        LoadedInput loaded = new LoadedInput();
        int loadIndex = 0;
        for (StoredInput input : inputs) {
            boolean looksLikeArchive = input.content.length >= 8
                    && new String(input.content, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).equals("!<arch>\n");
            InputItem item = new InputItem(loadIndex++, input.id, input.name, looksLikeArchive, input.content);
            try {
                if (looksLikeArchive) {
                    ArchiveReader reader = new ArchiveReader(input.content);
                    List<ArchiveMember> members = reader.members();
                    item.memberList = members;
                    int memberIndex = 0;
                    for (ArchiveMember member : members) {
                        try {
                            ElfFile elf = new ElfReader(member.content()).parse(member.name());
                            item.memberElves.put(member.name(), elf);
                        } catch (RuntimeException ex) {
                            loaded.diagnostics.add(new Diagnostic("warn", "CORRUPT_MEMBER",
                                    "archive member " + member.name() + " 已隔离: " + ex.getMessage(),
                                    input.id));
                        }
                        memberIndex++;
                    }
                } else {
                    item.elf = new ElfReader(input.content).parse(input.name);
                }
            } catch (RuntimeException ex) {
                item.corruptReason = ex.getMessage();
                loaded.diagnostics.add(new Diagnostic("error", "CORRUPT_OBJECT",
                        input.name + " 已隔离: " + ex.getMessage(), input.id));
            }
            loaded.items.add(item);
        }
        return loaded;
    }
}
