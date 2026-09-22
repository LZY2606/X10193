package com.court.resolution;

import java.util.LinkedHashMap;
import java.util.Map;

/** One step in the replayable timeline. */
public class TraceEvent {
    public enum Kind {
        INPUT_PARSED,
        INPUT_QUARANTINED,
        ARCHIVE_CONSIDERED,
        ARCHIVE_EXTRACTED,
        COMDAT_DEDUPED,
        CANDIDATES_COLLECTED,
        CANDIDATE_REJECTED,
        VERDICT,
        COMMON_MERGED,
        COPY_RELOCATION_CREATED,
        SYMBOL_TABLE_UPDATE,
        POLICY_APPLIED
    }

    public final int sequence;
    public final Kind kind;
    public final String summary;
    public final Map<String, Object> detail = new LinkedHashMap<>();

    public TraceEvent(int sequence, Kind kind, String summary) {
        this.sequence = sequence;
        this.kind = kind;
        this.summary = summary;
    }
}
