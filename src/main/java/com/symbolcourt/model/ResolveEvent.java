package com.symbolcourt.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One step of the arbitration replay. Events are stored in order and replayed
 * cumulatively on the page (archive extraction, COMDAT drop, candidate
 * elimination, winner selection, symbol-table update).
 */
public final class ResolveEvent {
    public enum Type {
        UNIT_LOADED,
        ARCHIVE_SCAN,
        ARCHIVE_EXTRACTED,
        ARCHIVE_CYCLE_RETRY,
        ARCHIVE_NOT_EXTRACTED,
        COMDAT_SELECTED,
        COMDAT_DISCARDED,
        REFERENCE_OPENED,
        CANDIDATE_CONSIDERED,
        CANDIDATE_ELIMINATED,
        SYMBOL_TABLE_UPDATE,
        WINNER_SELECTED,
        VERDICT,
        WARNING,
        POLICY
    }

    private final int sequence;
    private final Type type;
    private final String title;
    private final String detail;
    private final Map<String, Object> payload = new LinkedHashMap<>();

    public ResolveEvent(int sequence, Type type, String title, String detail) {
        this.sequence = sequence;
        this.type = type;
        this.title = title;
        this.detail = detail;
    }

    public int getSequence() { return sequence; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public Map<String, Object> getPayload() { return payload; }

    public ResolveEvent with(String key, Object value) {
        payload.put(key, value);
        return this;
    }
}
