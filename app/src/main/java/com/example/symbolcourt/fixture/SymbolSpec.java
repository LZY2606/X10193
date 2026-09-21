package com.example.symbolcourt.fixture;

public record SymbolSpec(String name, String version, Binding binding, Type type,
                         Visibility visibility, int sectionIndex, long value, long size,
                         boolean common, boolean defined, boolean dynamic) {
    public enum Binding { LOCAL(0), GLOBAL(1), WEAK(2);
        final int value;
        Binding(int value) { this.value = value; }
    }

    public enum Type { NOTYPE(0), OBJECT(1), FUNC(2), SECTION(3), COMMON(5);
        final int value;
        Type(int value) { this.value = value; }
    }

    public enum Visibility { DEFAULT(0), INTERNAL(1), HIDDEN(2), PROTECTED(3);
        final int value;
        Visibility(int value) { this.value = value; }
    }

    public static SymbolSpec globalFunc(String name) {
        return new SymbolSpec(name, null, Binding.GLOBAL, Type.FUNC, Visibility.DEFAULT, 1, 0, 1,
                false, true, false);
    }

    public static SymbolSpec weakFunc(String name) {
        return new SymbolSpec(name, null, Binding.WEAK, Type.FUNC, Visibility.DEFAULT, 1, 0, 1,
                false, true, false);
    }

    public static SymbolSpec undef(String name, Binding binding, String version) {
        return new SymbolSpec(name, version, binding, Type.NOTYPE, Visibility.DEFAULT, 0, 0, 0,
                false, false, true);
    }

    public static SymbolSpec common(String name, long size) {
        return new SymbolSpec(name, null, Binding.GLOBAL, Type.OBJECT, Visibility.DEFAULT, 0xfff2,
                0, size, true, false, false);
    }
}
