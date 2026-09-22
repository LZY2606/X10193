package com.symbolcourt.model;

/** Final verdict for a reference. */
public enum ResolutionStatus {
    RESOLVED,
    RESOLVED_WEAK,      // weak ref left undefined intentionally
    AMBIGUOUS,          // multiple equally-ranked providers; ambiguity preserved
    CONFLICT,           // e.g. duplicate strong definitions
    UNRESOLVED,         // strong ref with no provider
    VERSION_UNMET,      // requested version is not provided by any loaded object
    LOCAL_NOT_EXPORTED, // hidden/local symbol cannot be satisfied inter-module
    COPY_PROTECTED      // COPY relocation cannot bind to protected data
}
