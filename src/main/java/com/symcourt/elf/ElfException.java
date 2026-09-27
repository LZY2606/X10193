package com.symcourt.elf;

/** Raised when an ELF/archive blob is corrupt or exceeds safe bounds. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
