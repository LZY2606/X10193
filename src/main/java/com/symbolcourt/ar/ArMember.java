package com.symbolcourt.ar;

/** archive 中的一个成员（惰性保存其字节）。 */
public class ArMember {
    public String name;
    public long dataOffset;
    public long size;
    public byte[] data;
}
