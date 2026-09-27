package com.symbolcourt.elf;

/** Raised when an ELF object is malformed. The importer isolates the offending object only. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
