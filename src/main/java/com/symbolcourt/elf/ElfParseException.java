package com.symbolcourt.elf;

/** Thrown when an object file cannot be parsed. Must never escape past the unit boundary. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }

    public ElfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
