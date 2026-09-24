package com.verdict.elf;

/** Thrown when an ELF object is malformed. Carries enough detail to isolate the bad object. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
