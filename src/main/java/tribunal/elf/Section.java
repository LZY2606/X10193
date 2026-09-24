package tribunal.elf;

public class Section {
    public int nameOffset;
    public String name;
    public int type;
    public long flags;
    public long addr;
    public long offset;
    public long size;
    public int link;
    public int info;
    public long addralign;
    public long entsize;
    /** COMDAT 判定：section 所属 group 是否在本对象内被丢弃。 */
    public boolean discarded;
    public int groupIndex = -1;

    public boolean isGroupComdat() { return false; }
}
