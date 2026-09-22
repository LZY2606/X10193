package local.symboltribunal.elf;

class ElfParseException extends RuntimeException {
    ElfParseException(String message) {
        super(message);
    }

    ElfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
