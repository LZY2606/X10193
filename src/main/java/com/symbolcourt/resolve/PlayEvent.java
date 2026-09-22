package com.symbolcourt.resolve;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One replayable step: archive member extraction, COMDAT selection, symbol
 * table update or a verdict decision.
 */
public final class PlayEvent {
    public final int index;
    public final String kind;
    public final String title;
    public final String detail;
    public final Map<String, Object> state = new LinkedHashMap<>();

    public PlayEvent(int index, String kind, String title, String detail) {
        this.index = index;
        this.kind = kind;
        this.title = title;
        this.detail = detail;
    }

    public PlayEvent put(String k, Object v) {
        state.put(k, v);
        return this;
    }
}
