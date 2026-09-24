package arb.ar;

public record ArchiveMember(String name, byte[] data, long offset, boolean parseError,
                            String error) {
}
