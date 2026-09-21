package org.tribunal.model;

import java.util.List;

public record VersionDefinition(int index, List<String> names, boolean base, boolean hidden) {
}
