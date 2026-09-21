package court.elf;

import java.util.ArrayList;
import java.util.List;

/** A single ELF member, either a standalone object or one extracted member of an archive. */
public class LoadedBinary {
    public String sourceFile;     // original upload file name
    public String memberName;     // null for standalone; "archive.a(member.o)" label otherwise
    public int memberOrder = -1;
    public ElfImage image;
    public boolean corrupt;
    public String error;
    public String unitId;         // stable identity across schemes: file + member

    public String displayName() {
        return memberName == null ? sourceFile : sourceFile + "(" + memberName + ")";
    }
}
