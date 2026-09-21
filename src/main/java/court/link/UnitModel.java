package court.link;

import court.elf.ElfImage;

import java.util.ArrayList;
import java.util.List;

/** A linkable unit after extraction: a standalone ELF or one archive member. */
public class UnitModel {
    public String unitId;
    public String displayName;
    public String sourceFile;
    public String memberName;
    public int inputOrder;        // position of its LoadedFile
    public int resolvedOrder = -1;
    public String kind;           // REL | DYN | EXEC
    public int machine;
    public boolean fromArchive;
    public boolean extracted;     // archive member pulled lazily
    public boolean discardedComdat;
    public String comdatSignature;
    public boolean incompatible;
    public boolean corrupt;
    public String error;
    public ElfImage image;
    public final List<Provider> providers = new ArrayList<>();
    public final List<Reference> references = new ArrayList<>();
    public final List<String> needed = new ArrayList<>();
    public String soname;
}
