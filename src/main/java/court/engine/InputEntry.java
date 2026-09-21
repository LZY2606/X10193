package court.engine;

import court.elf.ElfObject;

/**
 * 引擎的一个显式输入：可能是独立 ELF，也可能是 archive（members 非空时）。
 * order 即用户指定的显式加载顺序。
 */
public final class InputEntry {
    public final String uploadId;
    public final String name;
    public final ElfObject elf; // archive 时为 null
    public final java.util.List<ArchiveMemberInput> members;
    public final int order;

    public InputEntry(String uploadId, String name, ElfObject elf,
                      java.util.List<ArchiveMemberInput> members, int order) {
        this.uploadId = uploadId;
        this.name = name;
        this.elf = elf;
        this.members = members;
        this.order = order;
    }

    public boolean isArchive() {
        return members != null;
    }

    public static final class ArchiveMemberInput {
        public final String name;
        public final ElfObject elf;
        public final String parseError;

        public ArchiveMemberInput(String name, ElfObject elf, String parseError) {
            this.name = name;
            this.elf = elf;
            this.parseError = parseError;
        }
    }
}
