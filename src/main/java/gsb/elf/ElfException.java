package gsb.elf;

/** Thrown when an ELF object is malformed. Carries enough context to isolate the bad object. */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
