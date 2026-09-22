package com.symbolcourt.elf;

public enum Kind {
    RELOCATABLE,   // ET_REL
    EXECUTABLE,    // ET_EXEC
    SHARED,        // ET_DYN
    CORE           // ET_CORE
}
