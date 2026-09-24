package com.tribunal.core;

/** 裁决过程的一步，用于页面逐步播放。 */
public class ResolutionEvent {
    public enum Kind {
        LOAD_OBJECT, COMDAT_DEDUP, SYMBOL_UPDATE, ARCHIVE_EXTRACT,
        COPY_RELOC, VERDICT, ISOLATE_CORRUPT
    }

    public int seq;
    public Kind kind;
    public String message;

    public ResolutionEvent(int seq, Kind kind, String message) {
        this.seq = seq;
        this.kind = kind;
        this.message = message;
    }
}
