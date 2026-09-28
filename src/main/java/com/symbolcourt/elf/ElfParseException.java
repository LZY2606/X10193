package com.symbolcourt.elf;

/** Thrown when a binary cannot be parsed; the faulty input unit is isolated from others. */
public class ElfParseException extends Exception {
    public ElfParseException(String message) { super(message); }
    public ElfParseException(String message, Throwable cause) { super(message, cause); }
}
