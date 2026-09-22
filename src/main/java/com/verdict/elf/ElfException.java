package com.verdict.elf;

/** Raised when an ELF object is malformed. Corrupt objects are isolated by callers. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
}
