package court.symbol.elf;

import java.util.List;

public record VersionNeed(String library, List<Requirement> requirements) {
    public record Requirement(int index, String name, boolean defaultVersion, int flags) {
    }
}
