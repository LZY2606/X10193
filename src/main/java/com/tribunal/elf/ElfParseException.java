package com.tribunal.elf;

/** 解析损坏或越界的 ELF 对象时抛出；只隔离该对象自身。 */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
