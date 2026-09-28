package com.symbolcourt.model;

public final class Enums {
    private Enums() {}

    public enum ElfClass { ELF32, ELF64 }
    public enum DataEncoding { LSB, MSB }
    public enum MachineType { X86_64, I386, OTHER }
    public enum ObjectKind { RELOCATABLE, EXECUTABLE, SHARED, CORE, UNKNOWN }
    public enum InputKind { OBJECT, ARCHIVE, SHARED, EXECUTABLE }

    public enum SymbolBinding { LOCAL, GLOBAL, WEAK, GNU_UNIQUE }
    public enum SymbolType { NOTYPE, OBJECT, FUNC, SECTION, FILE, TLS, IFUNC, OTHER }
    public enum SymbolVisibility { DEFAULT, INTERNAL, HIDDEN, PROTECTED }
    public enum SymbolShndx { UNDEF, ABS, COMMON, SECTION_INDEX }

    public enum GroupSelection { COMDAT, OTHER, NONE }

    public enum ArchivePolicy { SEQUENTIAL, GROUP_FIXPOINT }
    public enum ProtectedDataPolicy { COPY_RELOC, ERROR, NORMAL_INTERP }
    public enum UnversionedBindPolicy { FIRST_DEFAULT, ANY, ERROR }

    public enum DecisionStatus {
        RESOLVED, AMBIGUOUS, UNRESOLVED, WEAK_NULL, UNRESOLVED_VERSION,
        COPY_RELOCATION, PROTECTED_LOCAL, NO_REFS
    }

    public enum EliminationReason {
        VISIBILITY_HIDDEN,
        VISIBILITY_LOCAL,
        NOT_DEFINED,
        VERSION_MISMATCH,
        VERSYM_HIDDEN,
        COMDAT_DISCARDED,
        LATER_IN_LOAD_ORDER,
        LOWER_BINDING,
        COMMON_LOWER_PRIORITY,
        ARCHIVE_MEMBER_NOT_EXTRACTED,
        SELF_PRECEDENCE,
        PREEMPTED,
        OTHER
    }
}
