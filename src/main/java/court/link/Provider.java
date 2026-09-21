package court.link;

/** A defining occurrence of a symbol that can satisfy a reference. */
public class Provider {
    public String unitId;          // file or file(member)
    public String displayName;
    public String sourceFile;
    public String name;
    public String version;         // version name or ""
    public boolean nonDefault;     // hidden version definition
    public String binding;         // GLOBAL/WEAK/LOCAL
    public String visibility;      // DEFAULT/PROTECTED/HIDDEN/INTERNAL
    public String type;            // OBJECT/FUNC/...
    public String definitionClass; // OBJECT_DEF | COMMON | SHARED_DEF
    public long size;
    public int sectionIndex;
    public String sectionName;
    public String comdatSignature; // belongs to a COMDAT group with this signature
    public boolean interposable = true; // shared exportable defs can be preempted
    public long predictedAddress;  // assigned after placement
    public int loadOrder;          // final resolved unit order
    public int defineOrder;

    public boolean isStrongObject() { return "OBJECT_DEF".equals(definitionClass) && "GLOBAL".equals(binding); }
    public boolean isWeak() { return "WEAK".equals(binding); }
    public boolean isCommon() { return "COMMON".equals(definitionClass); }
}
