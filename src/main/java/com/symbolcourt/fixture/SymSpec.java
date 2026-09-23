package com.symbolcourt.fixture;

/** 构造 fixture 符号的规格。 */
public class SymSpec {
    public String name;
    public int bind;
    public int type;
    public int visibility = 0;
    public boolean defined = true;
    public String section = ".text";
    public long value;
    public long size;
    public long commonAlign;
    public String version;
    public boolean versionHidden;
    public boolean versionRequired;
    public boolean dynamicOnly;
    public boolean staticOnly;
    public String neededFromFile;
}
