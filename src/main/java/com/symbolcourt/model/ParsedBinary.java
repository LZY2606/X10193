package com.symbolcourt.model;

import java.util.List;

public record ParsedBinary(
        String binaryId,
        String originalFilename,
        String format,
        boolean corrupt,
        String error,
        List<ElfObject> objects) {
}
