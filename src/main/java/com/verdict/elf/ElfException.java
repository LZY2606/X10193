package com.verdict.elf;

/** Thrown when an object file is malformed. Callers isolate the offending object only. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
