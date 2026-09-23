package com.tribunal.archive;

/** One member of an {@code ar(1)} archive: its display name and raw bytes. */
public record ArMember(String name, long dataOffset, byte[] data) {
}
