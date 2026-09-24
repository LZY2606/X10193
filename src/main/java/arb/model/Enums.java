package arb.model;

public final class Enums {
    private Enums() {
    }

    public enum Binding {
        LOCAL, GLOBAL, WEAK, OS, PROC, UNKNOWN;

        public static Binding from(int b) {
            return switch (b) {
                case 0 -> LOCAL;
                case 1 -> GLOBAL;
                case 2 -> WEAK;
                case 10, 11, 12 -> OS;
                case 13, 14, 15 -> PROC;
                default -> UNKNOWN;
            };
        }
    }

    public enum SymbolType {
        NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, UNKNOWN;

        public static SymbolType from(int t) {
            return switch (t) {
                case 0 -> NOTYPE;
                case 1 -> OBJECT;
                case 2 -> FUNC;
                case 3 -> SECTION;
                case 4 -> FILE;
                case 5 -> COMMON;
                case 6 -> TLS;
                default -> UNKNOWN;
            };
        }
    }

    public enum Visibility {
        DEFAULT, INTERNAL, HIDDEN, PROTECTED;

        public static Visibility from(int v) {
            return switch (v) {
                case 0 -> DEFAULT;
                case 1 -> INTERNAL;
                case 2 -> HIDDEN;
                case 3 -> PROTECTED;
                default -> DEFAULT;
            };
        }
    }

    public enum InputKind {
        OBJECT, SHARED, EXEC, ARCHIVE
    }

    public enum LinkMode {
        STATIC, DYNAMIC
    }
}
