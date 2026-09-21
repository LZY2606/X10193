package court.model;

/**
 * 损坏对象的隔离记录：只隔离自身，其它对象照常裁决。
 */
public final class BadObject {
    public final String uploadId;
    public final String name;
    public final String error;

    public BadObject(String uploadId, String name, String error) {
        this.uploadId = uploadId;
        this.name = name;
        this.error = error;
    }
}
