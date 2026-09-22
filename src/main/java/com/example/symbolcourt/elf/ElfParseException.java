package com.example.symbolcourt.elf;

/** Thrown when an object file is malformed. Corrupt objects are isolated, never fatal to the case. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
