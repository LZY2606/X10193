package court.db;

public record ObjectRecord(String sha, String name, String kind, int size,
                           String corruptReason, byte[] data) {
}
