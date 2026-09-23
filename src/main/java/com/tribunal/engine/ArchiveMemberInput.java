package com.tribunal.engine;

/** One member of an archive in the load list. */
public record ArchiveMemberInput(String memberName, byte[] bytes) {
}
