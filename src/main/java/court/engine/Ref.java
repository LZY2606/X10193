package court.engine;

import court.elf.Reloc;

import java.util.ArrayList;
import java.util.List;

/** An undefined (or preemptible) reference produced by one unit against one symbol. */
public final class Ref {
    public Ref() {}
    public String id;
    public String unitId;
    public String unitName;
    public String name;
    /** Required version name; null = unversioned reference. */
    public String version;
    public int bind;
    public boolean weak;
    public boolean internal;
    public boolean copyReloc;
    public final List<RelocSite> relocs = new ArrayList<>();
    public String requiredFromFile;

    public static final class RelocSite {
        public String section;
        public long offset;
        public String type;
        public Long addend;
        public transient Reloc reloc;
    }
}
