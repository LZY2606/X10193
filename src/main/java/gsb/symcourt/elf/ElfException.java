package gsb.symcourt.elf;

/** Thrown when an object cannot be parsed. A corrupt object isolates itself. */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
