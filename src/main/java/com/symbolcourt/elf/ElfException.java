package com.symbolcourt.elf;

/**
 * Thrown when an input object is malformed in a way that prevents safe parsing.
 * A failure here isolates only the offending object (or archive member).
 */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
