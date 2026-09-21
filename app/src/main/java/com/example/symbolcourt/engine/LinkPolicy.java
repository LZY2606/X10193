package com.example.symbolcourt.engine;

public enum LinkPolicy {
    GNU("GNU 风格：重复全 archive 扫描，静态对象优先，允许 weak 未满足", true),
    STRICT_ORDER("严格加载顺序：每个 archive 只扫描一次", false),
    DYNAMIC_SCOPE("动态作用域：共享对象之间按加载顺序抢占", true);

    private final String displayName;
    private final boolean rescanArchives;

    LinkPolicy(String displayName, boolean rescanArchives) {
        this.displayName = displayName;
        this.rescanArchives = rescanArchives;
    }

    public String displayName() {
        return displayName;
    }

    public boolean rescanArchives() {
        return rescanArchives;
    }
}
