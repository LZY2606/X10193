package com.symbolcourt.elf;

/** Thrown when an object is malformed, truncated, or has out-of-range indexes. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) { super(message); }
}
