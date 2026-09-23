package com.example.symbolcourt.parser;

public class ElfFormatException extends Exception {
    public ElfFormatException(String message) {
        super(message);
    }

    public ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
