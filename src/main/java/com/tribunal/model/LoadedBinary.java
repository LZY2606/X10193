package com.tribunal.model;

import java.util.List;

public record LoadedBinary(
        String name,
        String kind,
        byte[] bytes,
        List<ElfData> objects,
        String parseError,
        int order
) {}
