package com.symcourt.elf;

/** A corrupt or unsupported object. Must be isolated: never crash the whole import. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
}
