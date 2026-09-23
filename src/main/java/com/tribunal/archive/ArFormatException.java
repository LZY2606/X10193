package com.tribunal.archive;

/** Thrown when an archive is not a valid {@code !<arch>\n} ar(1) file. */
public class ArFormatException extends Exception {
    public ArFormatException(String message) {
        super(message);
    }
}
