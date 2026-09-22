package com.symbolcourt.model;

import java.util.List;

/** User-supplied definition of one arbitration scheme: ordered files plus policy. */
public record SchemeInput(String name, List<UploadedFile> files, List<Integer> order,
                          Policy policy, String parentSchemeId) {
    public record UploadedFile(String fileId, String originalName) {
    }
}
