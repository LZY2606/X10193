package com.symtribunal.elf;

/** Thrown for any truncated or structurally invalid ELF input; the offending object is isolated. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
