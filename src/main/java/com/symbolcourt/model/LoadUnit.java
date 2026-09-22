package com.symbolcourt.model;

import com.symbolcourt.elf.ElfFile;

import java.util.ArrayList;
import java.util.List;

/**
 * A single participant in a resolution: an ELF object/shared object, or one
 * archive member extracted from a static library. Parsing failure is recorded
 * here so that a corrupt unit is isolated and never aborts the scheme.
 */
public final class LoadUnit {
    private final String unitId;
    private final String displayName;
    private final InputEntry.Kind kind;
    private int loadIndex;
    private final String archiveId;
    private final String archiveMemberName;
    private final int archiveOrder;
    private ElfFile elf;
    private ElfSummary summary;
    private boolean parseOk;
    private String parseError;
    private final List<String> notes = new ArrayList<>();
    private boolean loaded;
    private String extractedBy; // reference id that triggered archive extraction

    private LoadUnit(Builder b) {
        this.unitId = b.unitId;
        this.displayName = b.displayName;
        this.kind = b.kind;
        this.loadIndex = b.loadIndex;
        this.archiveId = b.archiveId;
        this.archiveMemberName = b.archiveMemberName;
        this.archiveOrder = b.archiveOrder;
    }

    public String getUnitId() { return unitId; }
    public String getDisplayName() { return displayName; }
    public InputEntry.Kind getKind() { return kind; }
    public int getLoadIndex() { return loadIndex; }
    public String getArchiveId() { return archiveId; }
    public String getArchiveMemberName() { return archiveMemberName; }
    public int getArchiveOrder() { return archiveOrder; }
    public ElfFile getElf() { return elf; }
    public ElfSummary getSummary() { return summary; }
    public boolean isParseOk() { return parseOk; }
    public String getParseError() { return parseError; }
    public List<String> getNotes() { return notes; }
    public boolean isLoaded() { return loaded; }
    public String getExtractedBy() { return extractedBy; }

    public void markParsed(ElfFile elf) {
        this.elf = elf;
        this.parseOk = true;
        this.loaded = true;
        this.summary = ElfSummaries.of(elf);
    }

    public void markCorrupt(String error) {
        this.parseError = error;
        this.parseOk = false;
    }

    public void markExtracted(String referenceId) {
        this.loaded = true;
        this.extractedBy = referenceId;
    }

    public boolean isArchiveMember() {
        return archiveId != null;
    }

    public void assignLoadIndex(int idx) {
        this.loadIndex = idx;
    }

    public static Builder builder(String unitId, InputEntry.Kind kind, int loadIndex) {
        return new Builder(unitId, kind, loadIndex);
    }

    public static final class Builder {
        private final String unitId;
        private final InputEntry.Kind kind;
        private int loadIndex;
        private String displayName;
        private String archiveId;
        private String archiveMemberName;
        private int archiveOrder;

        private Builder(String unitId, InputEntry.Kind kind, int loadIndex) {
            this.unitId = unitId;
            this.kind = kind;
            this.loadIndex = loadIndex;
            this.displayName = unitId;
        }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder archive(String archiveId, String member, int order) {
            this.archiveId = archiveId;
            this.archiveMemberName = member;
            this.archiveOrder = order;
            return this;
        }

        public LoadUnit build() {
            return new LoadUnit(this);
        }
    }
}
