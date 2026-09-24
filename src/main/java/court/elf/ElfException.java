package court.elf;

/** Thrown when a binary cannot be parsed. Callers must isolate only the offending object. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
