package court.symbol.elf;

public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
