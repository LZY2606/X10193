package com.verdictcourt.elf;

/** Raised when an ELF object is malformed or exceeds length/index bounds. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
