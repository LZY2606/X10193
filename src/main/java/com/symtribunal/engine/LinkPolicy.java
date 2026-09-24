package com.symtribunal.engine;

/** Target platform symbol-binding policies selectable when creating a resolution scheme. */
public enum LinkPolicy {
    /** GNU-style static-first resolution: explicit objects beat archives, archives beat DSOs. */
    GNU_STATIC,
    /** Strict load-order dynamic resolution across every object the user provided. */
    LOAD_ORDER
}
