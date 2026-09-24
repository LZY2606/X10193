package com.symbolcourt.elf;

/** 解析 ELF/ar 时的可恢复异常：损坏对象只隔离自身。 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
