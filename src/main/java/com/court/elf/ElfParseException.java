package com.court.elf;

/** Thrown when an ELF image is truncated, malformed, or violates index bounds. */
public class ElfParseException extends Exception {
    public ElfParseException(String message) { super(message); }
}
