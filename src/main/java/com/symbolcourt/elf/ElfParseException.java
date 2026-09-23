package com.symbolcourt.elf;

/** Thrown when an ELF/archive blob is corrupt or out of bounds. Only isolates the offending object. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
