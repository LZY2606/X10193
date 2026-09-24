package com.symtribunal.elf;

/**
 * Thrown when a binary cannot be parsed as a sane ELF/archive object.
 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
