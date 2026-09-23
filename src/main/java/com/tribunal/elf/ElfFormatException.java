package com.tribunal.elf;

/** Thrown for any structurally invalid or truncated ELF object. */
public class ElfFormatException extends Exception {
    public ElfFormatException(String message) {
        super(message);
    }

    public ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
