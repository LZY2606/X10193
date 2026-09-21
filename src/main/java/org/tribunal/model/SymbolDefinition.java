package org.tribunal.model;

public record SymbolDefinition(String unitId, String unitName, BinaryKind unitKind, int ordinal,
                               String archiveUnit, String name, String binding, String visibility,
                               int sectionIndex, String sectionName, String specialSection,
                               long value, long size, boolean defined, boolean common,
                               String versionName, String versionKey, Integer rawVersionIndex,
                               boolean defaultVersion, boolean hiddenVersion,
                               String groupSignature, String providerKey) {
    public boolean canProvideGlobal() {
        if (!defined || common || name == null || name.isBlank()) {
            return false;
        }
        if ("LOCAL".equals(binding)) {
            return false;
        }
        if ("HIDDEN".equals(visibility) || "INTERNAL".equals(visibility)) {
            return false;
        }
        return true;
    }
}
