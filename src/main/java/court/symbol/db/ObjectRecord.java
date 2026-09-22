package court.symbol.db;

public record ObjectRecord(String sha256, String filename, String kind, long size,
                           byte[] content) {
}
