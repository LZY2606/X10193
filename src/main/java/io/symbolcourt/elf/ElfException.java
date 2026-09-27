package io.symbolcourt.elf;

/**
 * Thrown when a binary claims to be ELF but is truncated or structurally
 * invalid. A malformed individual object must never abort arbitration of the
 * other objects; callers isolate the failure at the object boundary.
 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
