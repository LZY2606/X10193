package com.symbolcourt.elf;

/** Thrown for malformed or truncated ELF input. */
public class ElfFormatException extends Exception {
    public ElfFormatException(String message) {
        super(message);
    }

    public ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
