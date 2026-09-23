package com.symbolcourt.linker;

/** 还原后的版本名视图。 */
public class VersionInfo {
    public String name;
    public boolean definedVersion;
    public boolean nonDefault;
    public boolean special;

    public VersionInfo(String name, boolean definedVersion, boolean nonDefault, boolean special) {
        this.name = name;
        this.definedVersion = definedVersion;
        this.nonDefault = nonDefault;
        this.special = special;
    }
}
