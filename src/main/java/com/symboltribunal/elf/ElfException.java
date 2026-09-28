package com.symboltribunal.elf;

/** Raised when an ELF file is malformed or violates bounds checks. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
