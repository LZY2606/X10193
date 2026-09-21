package court.link;

/** A needing occurrence: an undefined symbol or a dynamic relocation against a symbol. */
public class Reference {
    public String id;
    public String unitId;
    public String displayName;
    public String name;
    public String version;       // required version name or ""
    public String binding;       // GLOBAL/WEAK/LOCAL
    public String visibility;
    public boolean fromRelocation;
    public String relocType;      // e.g. R_X86_64_PLT32, R_X86_64_COPY, JUMP_SLOT...
    public long relocOffset;
    public String relocSection;
    public long addend;
    public boolean copyRelocation;
    public boolean jumpSlot;
    public boolean globDat;
    public boolean nonDefaultNeeded;
}
