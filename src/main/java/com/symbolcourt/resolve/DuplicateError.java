package com.symbolcourt.resolve;

import java.util.List;

/** Two strong definitions of one non-common symbol in the static link. */
public final class DuplicateError {
    public final String symbolName;
    public final List<Provider> definitions;

    public DuplicateError(String symbolName, List<Provider> definitions) {
        this.symbolName = symbolName;
        this.definitions = definitions;
    }
}
