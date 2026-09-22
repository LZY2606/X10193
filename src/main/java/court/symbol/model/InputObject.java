package court.symbol.model;

/**
 * One entry in the explicit load/link order given by the user.
 * kind = OBJECT (standalone ET_REL), SHARED (ET_DYN) or ARCHIVE (lazy members).
 */
public record InputObject(int order, String key, String label, String kind,
                          byte[] content) {
    public boolean archive() {
        return "ARCHIVE".equals(kind);
    }
}
