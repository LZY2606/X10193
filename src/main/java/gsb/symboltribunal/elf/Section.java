package gsb.symboltribunal.elf;

public record Section(
        int index,
        String name,
        int type,
        long flags,
        long addr,
        long offset,
        long size,
        int link,
        int info,
        long addralign,
        long entsize) {

    public boolean is(int wanted) {
        return type == wanted;
    }
}
