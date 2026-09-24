package court.engine;

import court.elf.ArArchive;
import court.elf.ArMember;
import court.elf.ArReader;
import court.elf.ElfException;
import court.elf.ElfObject;
import court.elf.ElfReader;

import java.util.ArrayList;
import java.util.List;

/** Turns explicitly ordered raw inputs into units; corrupt inputs are isolated, never fatal. */
final class Loader {

    static final class Loaded {
        final List<Unit> units = new ArrayList<>();
        final List<LinkResult.DependencyEdge> edges = new ArrayList<>();
        final List<ElfObject> elves = new ArrayList<>();
    }

    Loaded load(LinkResult result, List<InputEntry> inputs) {
        Loaded loaded = new Loaded();
        ElfReader elfReader = new ElfReader();
        ArReader arReader = new ArReader();
        long rank = 0;
        for (InputEntry in : inputs) {
            rank++;
            byte[] head = in.data;
            boolean isAr = head != null && head.length >= 8
                    && head[0] == '!' && head[1] == '<';
            if (isAr) {
                try {
                    ArArchive ar = arReader.parse(in.data);
                    in.detectedKind = "ARCHIVE";
                    long memberRank = 0;
                    for (ArMember m : ar.members) {
                        memberRank++;
                        Unit u = memberUnit(in, m, rank, memberRank, elfReader, result);
                        loaded.units.add(u);
                        if (u.corruptReason == null) {
                            LinkResult.DependencyEdge e = new LinkResult.DependencyEdge();
                            e.from = in.name;
                            e.to = u.name;
                            e.kind = "ARCHIVE";
                            e.satisfied = false;
                            loaded.edges.add(e);
                        }
                    }
                } catch (ElfException ex) {
                    in.detectedKind = "CORRUPT";
                    in.corruptReason = ex.getMessage();
                    result.corrupt.put(in.name, "archive parse: " + ex.getMessage());
                }
            } else {
                Unit u = elfUnit(in, rank, elfReader, result);
                loaded.units.add(u);
                if (u.elf != null) loaded.elves.add(u.elf);
            }
        }
        return loaded;
    }

    private Unit elfUnit(InputEntry in, long rank, ElfReader reader, LinkResult result) {
        Unit u = new Unit();
        u.id = in.id;
        u.name = in.name;
        u.rankMajor = rank;
        u.rankMinor = 0;
        try {
            ElfObject elf = reader.parse(in.data);
            u.elf = elf;
            u.kind = switch (elf.type) {
                case 1 -> "ELF_REL";
                case 2 -> "ELF_EXEC";
                default -> "ELF_DYN";
            };
            in.detectedKind = u.kind;
            for (String need : elf.needed) {
                LinkResult.DependencyEdge e = new LinkResult.DependencyEdge();
                e.from = in.name;
                e.to = need;
                e.kind = "NEEDED";
                e.satisfied = false;
                result.edges.add(e);
            }
        } catch (ElfException ex) {
            u.kind = "CORRUPT";
            u.corruptReason = ex.getMessage();
            u.active = false;
            in.detectedKind = "CORRUPT";
            in.corruptReason = ex.getMessage();
            result.corrupt.put(in.name, ex.getMessage());
        }
        return u;
    }

    private Unit memberUnit(InputEntry archive, ArMember m, long rank, long memberRank,
                            ElfReader reader, LinkResult result) {
        Unit u = new Unit();
        u.id = archive.id + "::" + m.name;
        u.name = archive.name + "(" + m.name + ")";
        u.archiveId = archive.id;
        u.archiveName = archive.name;
        u.memberName = m.name;
        u.rankMajor = rank;
        u.rankMinor = memberRank;
        u.dormant = true;
        u.active = false;
        u.kind = "ARCHIVE_MEMBER";
        try {
            ElfObject elf = reader.parse(m.data);
            u.elf = elf;
        } catch (ElfException ex) {
            u.corruptReason = ex.getMessage();
            result.corrupt.put(u.name, ex.getMessage());
        }
        return u;
    }
}
