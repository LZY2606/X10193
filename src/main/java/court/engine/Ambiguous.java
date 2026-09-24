package court.engine;

import java.util.List;
import java.util.stream.Collectors;

/** Records a genuine tie without picking a filesystem-order winner. */
final class Ambiguous {
    static Def mark(LinkResult r, Ref ref, List<Def> ties, String code, String why) {
        Res res = ResTable.get(r).computeIfAbsent(Res.keyOf(ref),
                k -> new Res(k, ref.name, ref.version));
        res.status = "AMBIGUOUS";
        res.ambiguity = code;
        res.detail = why + "：" + ties.stream()
                .map(d -> d.unitName + (d.version != null ? "[" + d.version + "]" : ""))
                .collect(Collectors.joining(" / "));
        res.winner = null;
        return null;
    }
}
