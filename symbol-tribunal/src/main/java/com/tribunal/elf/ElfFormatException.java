package com.tribunal.elf;

/** Thrown when a binary fails structural ELF validation. Corruption is isolated per object. */
public class ElfFormatException extends RuntimeException {
    public ElfFormatException(String message) {
        super(message);
    }
    public ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
