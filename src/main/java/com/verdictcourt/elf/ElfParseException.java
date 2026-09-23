package com.verdictcourt.elf;

/** 解析失败（长度/索引越界、格式损坏）时抛出，仅隔离当前对象。 */
public class ElfParseException extends RuntimeException {
    public ElfParseException(String message) { super(message); }
}
