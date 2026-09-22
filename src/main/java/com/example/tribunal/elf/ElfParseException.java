package com.example.tribunal.elf;

class ElfParseException extends RuntimeException {
    ElfParseException(String message) {
        super(message);
    }

    ElfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
