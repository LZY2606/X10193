package com.court.resolution;

import com.court.model.ElfFile;
import com.court.model.ElfSymbol;

/**
 * A candidate definition: (object, symbol, version key).
 * Two providers with the same symbol name but different versions are
 * different candidates and never coalesce.
 */
public class Provider {
    public final ElfFile file;
    public final ElfSymbol symbol;
    /** null = unversioned; otherwise resolved version name (alias restored). */
    public final String versionName;
    /** true when this version name is a non-default (hidden) alias. */
    public final boolean nonDefaultVersion;
    public final int loadOrder;
    /** extraction round for archive members; 0 for explicitly loaded objects */
    public final int extractionRound;
    public long assignedAddress;

    public Provider(ElfFile file, ElfSymbol symbol, String versionName,
                    boolean nonDefaultVersion, int loadOrder, int extractionRound) {
        this.file = file;
        this.symbol = symbol;
        this.versionName = versionName;
        this.nonDefaultVersion = nonDefaultVersion;
        this.loadOrder = loadOrder;
        this.extractionRound = extractionRound;
    }

    public String fileLabel() { return file.id(); }
    public String symbolName() { return symbol.getName(); }
    public String key() {
        return symbol.getName() + (versionName == null ? "" : (nonDefaultVersion ? "@" : "@@") + versionName);
    }
}
