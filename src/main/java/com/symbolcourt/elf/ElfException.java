package com.symbolcourt.elf;

/** Raised for malformed / truncated ELF content. Always scoped to one object. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
}
