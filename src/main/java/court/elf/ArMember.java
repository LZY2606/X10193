package court.elf;

/** One member of an ar(5) archive; data is an owned byte array slice. */
public final class ArMember {
    public String name;
    public byte[] data;
    public long mtime;
    public int uid;
    public int gid;
    public int mode;
    public boolean longName;
}
