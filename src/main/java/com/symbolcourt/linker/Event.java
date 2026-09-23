package com.symbolcourt.linker;

import java.util.ArrayList;
import java.util.List;

/**
 * 可播放的裁决事件。snapshot 为该时刻全局符号表状态（播放用）。
 */
public class Event {
    public int step;
    public String kind; // INPUT | EXTRACT | INCLUDE | COMDAT | SYMBOL_UPDATE | RESOLVE | POLICY | DONE
    public String title;
    public String detail;
    public List<SymbolStatus> snapshot = new ArrayList<>();

    public Event() {}

    public Event(int step, String kind, String title, String detail) {
        this.step = step;
        this.kind = kind;
        this.title = title;
        this.detail = detail;
    }
}
