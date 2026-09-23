package gsb.tribunal.elf;

/** Thrown when an object cannot be safely parsed. Callers must isolate only that object. */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
    public ElfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
