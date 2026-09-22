package court.symbol.elf;

public class ElfReadException extends RuntimeException {
    public ElfReadException(String message) {
        super(message);
    }

    public ElfReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
