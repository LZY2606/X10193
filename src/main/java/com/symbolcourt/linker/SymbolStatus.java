package com.symbolcourt.linker;

/** 播放过程中全局符号表某名字的当前状态。 */
public class SymbolStatus {
    public String name;
    public String version;
    public Candidate current;
    public String state;
    public String note = "";

    public SymbolStatus() {}

    public SymbolStatus(String name, String version, Candidate current, String state, String note) {
        this.name = name;
        this.version = version;
        this.current = current;
        this.state = state;
        this.note = note;
    }
}
