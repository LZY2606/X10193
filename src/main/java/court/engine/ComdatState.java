package court.engine;

import java.util.HashMap;
import java.util.Map;

/** Per-run map: COMDAT signature -> winning unit name. */
final class ComdatState {
    private static final String KEY = "comdat.winners";

    @SuppressWarnings("unchecked")
    static Map<String, String> get(LinkResult r) {
        Object o = r.extra.get(KEY);
        if (o == null) {
            o = new HashMap<String, String>();
            r.extra.put(KEY, o);
        }
        return (Map<String, String>) o;
    }
}
