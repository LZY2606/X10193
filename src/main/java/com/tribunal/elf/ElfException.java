package com.tribunal.elf;

/** Thrown when an ELF/Archive blob is malformed. Callers isolate the offending object only. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
