package com.court.resolution;

import com.court.model.ElfFile;
import com.court.model.VersionDef;
import com.court.model.VersionReq;

/**
 * Restores version indices (as stored in .gnu.version) to human-readable names.
 *
 * Positive indices index the file's own version definitions; negative indices
 * (the low halfword interpreted as unsigned then converted) index the
 * version-need entries. Index 0/1 means unversioned/local/global.
 */
public final class VersionLookup {
    public static final int VER_NDX_LOCAL = 0;
    public static final int VER_NDX_GLOBAL = 1;

    private VersionLookup() { }

    public record VersionView(String name, boolean nonDefault, boolean defined) { }

    public static VersionView resolve(ElfFile f, int rawIndex) {
        if (rawIndex == VER_NDX_LOCAL || rawIndex == VER_NDX_GLOBAL) return null;
        if (rawIndex >= 2) {
            for (VersionDef d : f.getVersionDefs()) {
                if (d.index() == rawIndex) {
                    return new VersionView(d.name(), d.nonDefault(), true);
                }
            }
            return new VersionView("v" + rawIndex + "(def-missing)", false, true);
        }
        int needed = rawIndex & 0x7fff;
        for (VersionReq req : f.getVersionReqs()) {
            if ((req.otherIndex() & 0x7fff) == needed) {
                return new VersionView(req.name(), req.nonDefault(), false);
            }
        }
        return new VersionView("v" + needed + "(need-missing)", false, false);
    }
}
