package com.symbolcourt.elf;

/** Thrown when an ELF object is malformed or out of bounds. Only isolates the offending object. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
