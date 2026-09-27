package io.symbolcourt.elf;

import java.util.ArrayList;
import java.util.List;

/**
 * An object participating in a load plan. For an archive this record is the
 * container; each lazily extracted member becomes its own ParsedObject with
 * {@link #archiveMember} set.
 */
public class ParsedObject {
    public String key;              // unique stable key within a plan
    public String fileName;         // display path as imported
    public Kind kind;
    public ElfImage image;          // null for malformed objects or pure archive container
    public String parseError;       // non-null => isolated, never participates

    // archive container / membership
    public boolean archive;
    public List<ArchiveMember> members = new ArrayList<>();

    public String archiveName;      // set on extracted members
    public String archiveMember;
    public boolean extracted;       // archive member actually pulled in
    public int extractionRound;     // lazy extraction iteration, -1 if not extracted

    // load order (0 based) as given by the user
    public int order;

    public enum Kind { OBJECT, SHARED, ARCHIVE, UNKNOWN }

    public boolean usable() {
        return parseError == null && image != null;
    }

    public boolean isExtractedMember() {
        return archiveName != null;
    }

    /** Static archive table of contents entry. */
    public static class ArchiveMember {
        public String name;           // member file name within the archive
        public long headerOffset;
        public long dataOffset;
        public long dataSize;
        public ElfImage image;
        public String parseError;
        public boolean extracted;
        public int extractionRound = -1;
    }
}
