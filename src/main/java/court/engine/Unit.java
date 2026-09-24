package court.engine;

import court.elf.ElfObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One candidate provider: either a whole input ELF, or one extracted archive member.
 * rank is a lexicographic (major,minor) key derived solely from explicit load order.
 */
public final class Unit {
    public Unit() {}
    public String id;
    public String name;
    public String kind; // ELF_REL, ELF_EXEC, ELF_DYN, ARCHIVE_MEMBER
    public ElfObject elf;
    public String archiveId;
    public String archiveName;
    public String memberName;
    public long rankMajor;
    public long rankMinor;
    public boolean active = true;
    public boolean dormant;
    public int extractPass = -1;
    public String extractedFor;
    public String corruptReason;
    public final List<Def> defs = new ArrayList<>();
    public final List<Ref> refs = new ArrayList<>();
    public final List<UnitDiscard> discards = new ArrayList<>();

    public static final class UnitDiscard {
        public String signature;
        public String winnerUnit;
        public String reason;
        public final List<String> sections = new ArrayList<>();
    }
}
