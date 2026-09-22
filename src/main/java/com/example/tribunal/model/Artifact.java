package com.example.tribunal.model;

import java.util.List;

public record Artifact(
        String id,
        String filename,
        String sha256,
        ArtifactKind kind,
        List<BinaryObject> objects,
        List<String> parseErrors,
        long uploadedAt
) {
}
