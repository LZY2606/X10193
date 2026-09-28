package org.symcourt.elf;

/** Thrown when a binary cannot be parsed as the expected ELF/AR object. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
