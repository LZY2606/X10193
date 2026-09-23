package com.tribunal.engine;

/**
 * One element of the user supplied, explicitly ordered load list.
 * {@code displayName} is the label used in verdicts (file name / SONAME);
 * members are populated for archives.
 */
public record InputEntry(String displayName, InputKind kind, byte[] bytes,
                         java.util.List<ArchiveMemberInput> members) {
    public static InputEntry object(String name, byte[] bytes) {
        return new InputEntry(name, InputKind.OBJECT, bytes, java.util.List.of());
    }

    public static InputEntry shared(String name, byte[] bytes) {
        return new InputEntry(name, InputKind.SHARED, bytes, java.util.List.of());
    }

    public static InputEntry executable(String name, byte[] bytes) {
        return new InputEntry(name, InputKind.EXECUTABLE, bytes, java.util.List.of());
    }

    public static InputEntry archive(String name, java.util.List<ArchiveMemberInput> members) {
        return new InputEntry(name, InputKind.ARCHIVE, null, members);
    }
}
