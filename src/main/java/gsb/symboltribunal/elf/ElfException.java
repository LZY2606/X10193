package gsb.symboltribunal.elf;

/** Raised when a binary object is truncated or structurally invalid. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
