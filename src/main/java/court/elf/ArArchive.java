package court.elf;

import java.util.ArrayList;
import java.util.List;

/** Parsed static archive; members are kept raw and parsed lazily by the engine. */
public final class ArArchive {
    public byte[] raw;
    public final List<ArMember> members = new ArrayList<>();
}
