package court.store;

public final class UploadRow {
    public final String id;
    public final String name;
    public final String kind;
    public final byte[] raw;
    public final String sha256;
    public final String parseError;
    public final String createdAt;

    public UploadRow(String id, String name, String kind, byte[] raw, String sha256,
                     String parseError, String createdAt) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.raw = raw;
        this.sha256 = sha256;
        this.parseError = parseError;
        this.createdAt = createdAt;
    }
}
