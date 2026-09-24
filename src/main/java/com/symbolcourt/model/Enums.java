package com.symbolcourt.model;

public final class Enums {
    private Enums() {}

    public enum ElfClass { ELF32, ELF64 }
    public enum Endian { LITTLE, BIG }
    public enum ObjectKind { RELOCATABLE, EXECUTABLE, SHARED, UNKNOWN }
    public enum Machine { X86_64, AARCH64, GENERIC }

    public enum Binding { LOCAL, GLOBAL, WEAK, OTHER }
    public enum SymType { NOTYPE, OBJECT, FUNC, SECTION, FILE, COMMON, TLS, OTHER }
    public enum Visibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED, OTHER }

    public enum RelocKind {
        REGULAR, COPY, JUMP_SLOT, GLOB_DAT, RELATIVE, IRELATIVE, OTHER_DYNAMIC;

        public boolean isDynamicBinding() {
            return this != REGULAR;
        }
    }
}
