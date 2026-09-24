package com.symbolcourt.resolve;

public enum ResolutionStatus {
    RESOLVED,           // a concrete definition chosen
    AMBIGUOUS,         // several equally-good candidates, none chosen
    MULTIPLE_DEFINITION,// conflicting strong definitions
    UNDEFINED,         // no provider found
    UNSATISFIED_VERSION,// name found, but required version absent
    COPY_GENERATED,    // served by a copy relocation
    COMMON_BLOCK,      // provided by a tentative (common) definition
    LOCAL_ONLY         // only non-exported (hidden/local) definitions exist

}
