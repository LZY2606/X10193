package com.tribunal.core;

/** 目标平台策略。改变策略会生成新的方案版本。 */
public class Policy {
    /** 允许 copy relocation（可执行文件把共享库中的数据对象复制进 .bss）。 */
    public boolean copyRelocations = true;
    /** protected 符号是否可被外部抢占（某些平台语义）。 */
    public boolean protectedPreemptible = false;
    /** 多个 strong 定义并存时：true=取加载顺序首个（记录冲突），false=判为冲突未决。 */
    public boolean tolerateMultipleStrongDefs = false;
    /** 未定义弱符号解析为 0。 */
    public boolean undefinedWeakResolvesToZero = true;

    public String toJson() {
        return "{"
                + "\"copyRelocations\":" + copyRelocations + ","
                + "\"protectedPreemptible\":" + protectedPreemptible + ","
                + "\"tolerateMultipleStrongDefs\":" + tolerateMultipleStrongDefs + ","
                + "\"undefinedWeakResolvesToZero\":" + undefinedWeakResolvesToZero
                + "}";
    }

    public static Policy fromJson(String json) {
        Policy p = new Policy();
        if (json == null) return p;
        p.copyRelocations = !json.contains("\"copyRelocations\":false");
        p.protectedPreemptible = json.contains("\"protectedPreemptible\":true");
        p.tolerateMultipleStrongDefs = json.contains("\"tolerateMultipleStrongDefs\":true");
        p.undefinedWeakResolvesToZero = !json.contains("\"undefinedWeakResolvesToZero\":false");
        return p;
    }
}
