package com.tribunal.engine;

import com.tribunal.elf.ElfModel.Relocation;
import com.tribunal.elf.ElfModel.Symbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One bucket of references: a symbol name plus an optional requested version
 * and the SONAME that version requirement points at. Same name with a
 * different version is a different bucket.
 */
final class RefBucket {
    final String name;
    final String requestedVersion;
    final String versionFrom;
    /** True when at least one strong (GLOBAL) undefined reference exists. */
    boolean hasStrong;
    /** True when at least one weak undefined reference exists. */
    boolean hasWeak;
    /** True when a non-default (hidden bit) versioned reference exists. */
    boolean nonDefaultRef;

    final List<RefSite> sites = new ArrayList<>();

    RefBucket(String name, String requestedVersion, String versionFrom) {
        this.name = name;
        this.requestedVersion = requestedVersion;
        this.versionFrom = versionFrom;
    }

    static String key(String name, String version, String from) {
        return name + "\u0000" + (version == null ? "" : version)
                + "\u0000" + (from == null ? "" : from);
    }

    String key() {
        return key(name, requestedVersion, versionFrom);
    }

    record RefSite(ParsedUnit unit, Symbol symbol, Relocation relocation,
                   boolean localBind, String localNote) {
    }

    /** Ordered bucket registry, stable key order (insertion, never directory walk). */
    static final class Registry {
        final Map<String, RefBucket> buckets = new LinkedHashMap<>();

        RefBucket bucket(String name, String version, String from) {
            String k = key(name, version, from);
            return buckets.computeIfAbsent(k,
                    ignored -> new RefBucket(name, version, from));
        }
    }
}
