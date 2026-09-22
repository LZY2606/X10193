package com.symbolcourt.elf;

/**
 * Thrown when an ELF object violates structural assumptions. Every parser
 * entry validates lengths and indices before dereferencing them, so corrupt
 * objects fail closed instead of reading out of bounds.
 */
public class ElfFormatException extends RuntimeException {
    public ElfFormatException(String message) {
        super(message);
    }
}
