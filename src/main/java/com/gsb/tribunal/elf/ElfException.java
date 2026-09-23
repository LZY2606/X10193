package com.gsb.tribunal.elf;

/** Thrown when a binary cannot be parsed. The offending object is isolated, the rest of the run is unaffected. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
