package arb.engine;

public class RelocRef {
    private String object;
    private String member;
    private String section;
    private long offset;
    private long type;
    private long addend;

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getType() {
        return type;
    }

    public void setType(long type) {
        this.type = type;
    }

    public long getAddend() {
        return addend;
    }

    public void setAddend(long addend) {
        this.addend = addend;
    }
}
