package com.tribunal.engine;

import com.tribunal.archive.ArFormatException;
import com.tribunal.archive.ArMember;
import com.tribunal.archive.ArReader;
import com.tribunal.elf.ElfFormatException;
import com.tribunal.elf.ElfModel.ElfFile;
import com.tribunal.elf.ElfParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the ordered input list. Parse failures of one object or archive
 * member are isolated: they become {@link VerdictModel.InputError} entries
 * and never prevent the other inputs from being adjudicated.
 */
final class InputLoader {
    private final ElfParser parser = new ElfParser();

    static final class Loaded {
        final List<ParsedUnit> explicitUnits = new ArrayList<>();
        final List<ArchiveUnits> archives = new ArrayList<>();
        final List<VerdictModel.InputError> errors = new ArrayList<>();
    }

    static final class ArchiveUnits {
        final VerdictModel.UnitId id;
        final String name;
        final int order;
        final List<ParsedUnit> members = new ArrayList<>();

        ArchiveUnits(VerdictModel.UnitId id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }
    }

    Loaded load(List<InputEntry> entries) {
        Loaded loaded = new Loaded();
        int order = 0;
        for (InputEntry entry : entries) {
            switch (entry.kind()) {
                case OBJECT, SHARED, EXECUTABLE -> {
                    try {
                        ElfFile elf = parser.parse(entry.bytes(), entry.displayName());
                        InputKind kind = switch (elf.type()) {
                            case com.tribunal.elf.Elf.ET_DYN -> InputKind.SHARED;
                            case com.tribunal.elf.Elf.ET_EXEC -> InputKind.EXECUTABLE;
                            default -> InputKind.OBJECT;
                        };
                        loaded.explicitUnits.add(new ParsedUnit(
                                VerdictModel.UnitId.of(entry.displayName()), kind, elf,
                                null, null, order++, true));
                    } catch (ElfFormatException ex) {
                        loaded.errors.add(new VerdictModel.InputError(
                                VerdictModel.UnitId.of(entry.displayName()), "ELF 解析",
                                ex.getMessage()));
                    }
                }
                case ARCHIVE -> {
                    ArchiveUnits archive = new ArchiveUnits(
                            VerdictModel.UnitId.of(entry.displayName()),
                            entry.displayName(), order++);
                    List<ArchiveMemberInput> memberInputs = entry.members();
                    if (memberInputs.isEmpty() && entry.bytes() != null) {
                        try {
                            List<ArMember> raw = new ArReader().read(entry.bytes());
                            for (ArMember member : raw) {
                                memberInputs.add(new ArchiveMemberInput(member.name(),
                                        member.data()));
                            }
                        } catch (ArFormatException ex) {
                            loaded.errors.add(new VerdictModel.InputError(
                                    VerdictModel.UnitId.of(entry.displayName()),
                                    "archive 解析", ex.getMessage()));
                            memberInputs = List.of();
                        }
                    }
                    for (ArchiveMemberInput memberInput : memberInputs) {
                        VerdictModel.UnitId unitId = VerdictModel.UnitId.member(
                                entry.displayName(), memberInput.memberName());
                        try {
                            ElfFile elf = parser.parse(memberInput.bytes(),
                                    unitId.label());
                            archive.members.add(new ParsedUnit(unitId, InputKind.OBJECT,
                                    elf, entry.displayName(), memberInput.memberName(),
                                    archive.order, false));
                        } catch (ElfFormatException ex) {
                            loaded.errors.add(new VerdictModel.InputError(unitId,
                                    "ELF 解析（archive member）", ex.getMessage()));
                        }
                    }
                    loaded.archives.add(archive);
                }
            }
        }
        return loaded;
    }
}
