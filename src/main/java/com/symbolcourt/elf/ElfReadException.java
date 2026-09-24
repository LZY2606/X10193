package com.symbolcourt.elf;

/** Raised when an ELF image is truncated or structurally invalid. */
public class ElfReadException extends RuntimeException {
    public ElfReadException(String message) { super(message); }
}
