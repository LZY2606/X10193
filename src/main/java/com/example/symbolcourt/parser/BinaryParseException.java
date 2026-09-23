package com.example.symbolcourt.parser;

public class BinaryParseException extends RuntimeException {
    public BinaryParseException(String message) {
        super(message);
    }

    public BinaryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
