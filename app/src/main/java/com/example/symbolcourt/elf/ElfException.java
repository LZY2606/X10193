package com.example.symbolcourt.elf;

/** Thrown when a binary cannot be parsed. Callers isolate only the offending input. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
