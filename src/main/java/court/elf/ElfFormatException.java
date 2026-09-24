package court.elf;

/** Thrown when the bytes do not form a parseable ELF/ar object; the importer isolates it. */
public class ElfFormatException extends RuntimeException {
    public ElfFormatException(String message) {
        super(message);
    }

    public ElfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
