package court.engine;

import java.util.LinkedHashMap;
import java.util.Map;

final class ResTable {
    private static final String KEY = "res.table";

    @SuppressWarnings("unchecked")
    static Map<String, Res> get(LinkResult r) {
        Object o = r.extra.get(KEY);
        if (o == null) {
            o = new LinkedHashMap<String, Res>();
            r.extra.put(KEY, o);
        }
        return (Map<String, Res>) o;
    }
}
