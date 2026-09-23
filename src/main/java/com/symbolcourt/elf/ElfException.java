package com.symbolcourt.elf;

/** 损坏或不支持的 ELF 数据。只应隔离出错的对象本身。 */
public class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
