package com.symbolcourt.elf;

/** One member extracted from a static archive. */
public final class ArchiveMember {
    private final String name;
    private final int order;
    private final byte[] data;

    public ArchiveMember(String name, int order, byte[] data) {
        this.name = name;
        this.order = order;
        this.data = data;
    }

    public String getName() { return name; }
    public int getOrder() { return order; }
    public byte[] getData() { return data; }
}
