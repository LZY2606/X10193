package com.example.tribunal.core;

import java.util.List;
import java.util.Map;

public record Archive(
        List<ArchiveMember> members,
        Map<String, List<String>> symbolIndex
) {
}
