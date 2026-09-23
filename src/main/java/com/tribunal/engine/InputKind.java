package com.tribunal.engine;

/** Kind of an input unit in the explicit load list. */
public enum InputKind {
    /** Relocatable object linked directly. */
    OBJECT,
    /** Shared object participating in dynamic resolution. */
    SHARED,
    /** Archive whose members are extracted lazily. */
    ARCHIVE,
    /** Executable (its dynamic relocations drive copy relocation logic). */
    EXECUTABLE
}
