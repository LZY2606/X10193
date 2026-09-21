package court.model;

/**
 * 引用影响到的重定位条目（只读展示，来自原对象 relocation 节）。
 */
public final class AffectedReloc {
    public final String unitId;
    public final String unitName;
    public final String section;
    public final long offset;
    public final String type;
    public final long addend;
    public final boolean hasAddend;
    public final boolean copy;

    public AffectedReloc(String unitId, String unitName, String section, long offset,
                         String type, long addend, boolean hasAddend, boolean copy) {
        this.unitId = unitId;
        this.unitName = unitName;
        this.section = section;
        this.offset = offset;
        this.type = type;
        this.addend = addend;
        this.hasAddend = hasAddend;
        this.copy = copy;
    }
}
