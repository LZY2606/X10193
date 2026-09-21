package com.symcourt.elf;

/** 损坏或越界的 ELF 数据抛出的异常；解析层据此隔离单个对象。 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }
}
