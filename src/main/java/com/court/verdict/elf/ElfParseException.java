package com.court.verdict.elf;

/** 解析损坏或越界 ELF 时抛出；调用方据此隔离损坏对象。 */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) {
        super(message);
    }
}
