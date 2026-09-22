package gsb.symboltribunal.elf;

import java.util.List;

public record VersionNode(
        int index,
        boolean definition,
        String name,
        String parent,
        boolean weakNonDefault,
        boolean base,
        List<String> aliases,
        String fromNeed) {

    public boolean isUnversionedSpecial() {
        return index == ElfFile.VER_NDX_LOCAL || index == ElfFile.VER_NDX_GLOBAL;
    }
}
