package tribunal.engine;

/** Strategy presets the platform exposes. */
public enum LinkMode {
    /** ld-style static link of relocatable objects and lazily extracted archives. */
    STATIC,
    /** ld.so-style resolution across an explicit shared-object search order. */
    DYNAMIC
}
