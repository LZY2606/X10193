package gsb.tribunal.elf;

import java.util.List;

public record VersionNeed(String library, List<Aux> aux) {
    public record Aux(int index, String name, boolean hidden) {}
}
