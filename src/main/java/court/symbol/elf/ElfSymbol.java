package court.symbol.elf;

public class ElfSymbol {
    public final int index;
    public final String name;
    public final long value;
    public final long size;
    public final int binding;
    public final int type;
    public final int visibility;
    public final int sectionIndex;
    public final boolean dynamic;
    public int tableSectionIndex;
    public int versionIndex;
    public String versionName = "";
    public String versionLibrary = "";
    public boolean versionDefault = true;
    public boolean versionRequirement;
    public String sectionName = "";
    public String groupSignature = "";

    public ElfSymbol(int index, String name, long value, long size, int info, int other, int sectionIndex,
                     boolean dynamic) {
        this.index = index;
        this.name = name;
        this.value = value;
        this.size = size;
        this.binding = info >>> 4;
        this.type = info & 0x0f;
        this.visibility = other & 0x03;
        this.sectionIndex = sectionIndex;
        this.dynamic = dynamic;
        this.versionIndex = 1;
    }

    public boolean isLocal() {
        return binding == 0;
    }

    public boolean isWeak() {
        return binding == 2;
    }

    public boolean isDefined() {
        return sectionIndex != 0 && sectionIndex != 0xfff2;
    }

    public boolean isCommon() {
        return sectionIndex == 0xfff2;
    }

    public boolean isHiddenOutside() {
        return visibility == 2 || isLocal();
    }

    public boolean isProtected() {
        return visibility == 3;
    }

    public String key() {
        return name + "\u0000" + (versionName == null ? "" : versionName);
    }
}
