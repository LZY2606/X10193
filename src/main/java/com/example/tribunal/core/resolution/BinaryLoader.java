package com.example.tribunal.core.resolution;

import com.example.tribunal.core.elf.ArchiveParser;
import com.example.tribunal.core.elf.ElfParser;
import com.example.tribunal.core.elf.ElfModels.ElfObject;
import com.example.tribunal.core.model.TribunalModels.*;
import java.util.ArrayList;
import java.util.List;

public class BinaryLoader {
    public record LoadedBinary(LoadedInput input, List<ElfObject> objects, List<ElfObject> archiveMembers) {}

    public LoadedBinary load(String id, String fileName, byte[] bytes, int order) {
        if (ArchiveParser.isArchive(bytes)) {
            List<String> names = new ArrayList<>();
            List<ElfObject> objects = new ArrayList<>();
            String archiveError = null;
            try {
                var members = ArchiveParser.parse(bytes);
                for (var member : members) {
                    names.add(member.name());
                    try {
                        objects.add(ElfParser.parse(fileName + "(" + member.name() + ")", member.bytes()));
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                archiveError = e.getMessage();
            }
            return new LoadedBinary(new LoadedInput(id, fileName, InputKind.ARCHIVE, bytes, order, archiveError, List.copyOf(names)),
                    List.of(), List.copyOf(objects));
        }
        try {
            ElfObject object = ElfParser.parse(fileName, bytes);
            return new LoadedBinary(new LoadedInput(id, fileName, InputKind.ELF_OBJECT, bytes, order, null, List.of()),
                    List.of(object), List.of());
        } catch (Exception e) {
            return new LoadedBinary(new LoadedInput(id, fileName, InputKind.CORRUPT, bytes, order, e.getMessage(), List.of()),
                    List.of(), List.of());
        }
    }
}
