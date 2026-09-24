package court.engine;

import java.util.ArrayList;
import java.util.List;

/** Mutable per-reference-identity adjudication accumulator shared between phases. */
final class Res {
    final String key;
    final String name;
    final String version;
    String status = "UNRESOLVED";
    String detail;
    String stage;
    Def winner;
    final List<Ref> refs = new ArrayList<>();
    List<Def> staticCandidates = new ArrayList<>();
    List<Def> dynamicCandidates = new ArrayList<>();
    List<Elim> eliminations = new ArrayList<>();
    String ambiguity;
    /** For semantic diff between schemes: provider identity + resolved address. */
    String address;

    Res(String key, String name, String version) {
        this.key = key;
        this.name = name;
        this.version = version;
    }

    static String keyOf(Ref ref) {
        return ref.name + "\u0000" + (ref.version == null ? "" : ref.version);
    }

    static final class Elim {
        Def def;
        String reason;
        String stage;
    }
}
