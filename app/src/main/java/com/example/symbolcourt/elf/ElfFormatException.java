package com.example.symbolcourt.elf;

class ElfFormatException extends Exception {
    ElfFormatException(String message) {
        super(message);
    }

    ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
