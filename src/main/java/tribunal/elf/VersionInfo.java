package tribunal.elf;

import java.util.List;

/** Version definition / requirement tables reduced to named entries. */
public record VersionInfo(
        List<VersionDefinition> definitions,
        List<VersionRequirement> requirements
) {
    public static final VersionInfo EMPTY = new VersionInfo(List.of(), List.of());

    public record VersionDefinition(int index, String name, boolean base, boolean hidden,
                                    List<String> parents) {}

    public record VersionRequirement(String library, List<Entry> entries) {
        public record Entry(int index, int hash, String name, boolean weak) {}
    }
}
