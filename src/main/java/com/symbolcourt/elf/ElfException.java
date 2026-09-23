package com.symbolcourt.elf;

/** 损坏或越界的 ELF 输入抛出的异常；上层据此隔离单个对象。 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
