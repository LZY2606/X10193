package tribunal.elf;

public class Rela {
    public long offset;
    public long symIndex;
    public int type;
    public long addend;
    public boolean addendPresent;
    /** 所属 section（如 .rela.text）。 */
    public String section;
    /** 目标 section（应用到哪，由 sh_info 推断）。 */
    public String applySection;
    public boolean dynamic;
    public transient SymV sym;
    public int symTableKind;
    public int relocTypeRaw;
    public boolean discardedTarget;
}
