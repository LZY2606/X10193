package com.symbolcourt.resolve;

public enum UnitKind {
    OBJECT,    // ET_REL loaded explicitly or extracted from archive
    SHARED,    // ET_DYN shared object
    EXECUTABLE // ET_EXEC (used as the referencing copy-reloc program)
}
