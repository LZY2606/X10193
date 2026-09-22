package tribunal.engine;

/**
 * A concrete provider unit participating in one scheme.
 *
 * Scope order is the explicit load order provided by the user; ties are never
 * broken from filesystem traversal.
 */
public class Provider {
    public final int id;
    public final String label;         // file name or archive(member)
    public final String kind;          // OBJECT | DSO | EXEC | ARCHIVE_MEMBER
    public final String archive;       // null unless extracted member
    public final tribunal.elf.ElfObject elf;
    public final int scopeOrder;       // 0-based, ties resolved through this only
    public boolean extracted;          // archive members: initially false
    public boolean eliminatedByComdat;

    public Provider(int id, String label, String kind, String archive,
                    tribunal.elf.ElfObject elf, int scopeOrder, boolean extracted) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.archive = archive;
        this.elf = elf;
        this.scopeOrder = scopeOrder;
        this.extracted = extracted;
    }

    public String identity() {
        return archive != null ? archive + "(" + label + ")" : label;
    }
}
