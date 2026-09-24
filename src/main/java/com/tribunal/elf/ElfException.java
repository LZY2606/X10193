package com.tribunal.elf;

/** 损坏或越界的 ELF 输入。抛出后仅隔离该对象自身。 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
