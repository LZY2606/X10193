package tribunal.elf;

/** Thrown when bytes do not describe a parseable, in-bounds ELF/ar unit. */
public class ElfFormatException extends RuntimeException {
    public ElfFormatException(String message) { super(message); }
    public ElfFormatException(String message, Throwable cause) { super(message, cause); }
}
