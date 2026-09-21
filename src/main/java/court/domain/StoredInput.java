package court.domain;

/** One imported binary stored read-only inside a scheme. */
public class StoredInput {
    public Long id;
    public long schemeId;
    public int seq;
    public String fileName;
    public String kind;
    public long size;
    public String sha256;
    public byte[] data;
}
