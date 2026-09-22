package com.symbolcourt.elf;

/** Raised when an ELF/archive blob is malformed or exceeds safe bounds. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
