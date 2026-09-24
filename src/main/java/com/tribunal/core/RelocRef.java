package com.tribunal.core;

/** 受某符号裁决影响的一处重定位。 */
public class RelocRef {
    public String object;       // 发起重定位的对象
    public String section;      // 重定位节（如 .rela.text）
    public long offset;
    public long type;
    public String provider;     // 裁决后的提供者
}
