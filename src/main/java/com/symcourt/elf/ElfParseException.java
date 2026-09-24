package com.symcourt.elf;

/** Thrown when an object is malformed or exceeds length/index bounds. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
