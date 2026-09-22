package com.symbolcourt.resolve;

import com.symbolcourt.elf.Symbol;
import com.symbolcourt.elf.Visibility;

/**
 * A single symbol candidate. Two providers with the same symbol name but
 * different version names, binding, visibility or unit are distinct.
 */
public final class Provider {
    public final String unitId;
    public final String unitLabel;
    public final String symbolName;
    public final String version;          // null = unversioned
    public final String binding;          // GLOBAL/WEAK/LOCAL
    public final String visibility;       // DEFAULT/HIDDEN/PROTECTED/...
    public final String definedInSection; // .text / UND / COMMON / ...
    public final boolean common;
    public final long symbolValue;
    public final long size;
    public final boolean dynamic;
    public final int symbolTableIndex;

    public Provider(LoadedUnit unit, Symbol s, String sectionName) {
        this.unitId = unit.id;
        this.unitLabel = unit.label;
        this.symbolName = s.name;
        this.version = s.dynamic ? s.versionName : null;
        this.binding = s.binding.name();
        this.visibility = s.visibility.name();
        this.definedInSection = sectionName;
        this.common = s.isCommon();
        this.symbolValue = s.value;
        this.size = s.size;
        this.dynamic = s.dynamic;
        this.symbolTableIndex = s.tableIndex;
    }

    public String identity() {
        return unitId + "::" + symbolName + (version == null ? "" : "@" + version);
    }

    public boolean isHidden() {
        return Visibility.valueOf(visibility) == Visibility.HIDDEN
                || Visibility.valueOf(visibility) == Visibility.INTERNAL;
    }

    public boolean isProtected() {
        return Visibility.valueOf(visibility) == Visibility.PROTECTED;
    }

    public boolean isWeak() {
        return binding.equals("WEAK");
    }
}
