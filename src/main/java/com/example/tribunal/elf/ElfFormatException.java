package com.example.tribunal.elf;

/**
 * Raised when an input object is not a valid ELF or violates structural
 * bounds. Callers isolate the offending object instead of aborting the run.
 */
public class ElfFormatException extends Exception {
    public ElfFormatException(String message) {
        super(message);
    }

    public ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
