package com.symbolcourt.elf;

/** Raised when an ELF/ar structure is malformed or out of bounds. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
