package arb.model;

import java.util.List;

public final class VersionInfo {

    public record Def(int index, String name, String parent, boolean base, boolean hidden,
                      List<String> aliases) {
    }

    public record Need(int index, String name, String file) {
    }

    private VersionInfo() {
    }
}
