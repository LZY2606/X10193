package com.tribunal.core;

import java.util.ArrayList;
import java.util.List;

/** 一个符号键的裁决结论。 */
public class Verdict {
    public enum Status {
        RESOLVED,               // 唯一胜出者
        AMBIGUOUS,              // 多个同等候选，保留歧义（确定性取加载顺序首个，但标记歧义）
        CONFLICT,               // 多个 strong 定义冲突
        UNRESOLVED,             // 无人提供
        UNSATISFIED_VERSION,    // 有提供者但版本需求未满足
        WEAK_UNDEFINED          // 未定义弱符号，按策略解析为 0
    }

    public String symbolName;
    public String version;          // 需求版本（无版本引用为 null）
    public Status status;
    public String chosen;           // 最终提供者对象名（可能为 null）
    public String reason;           // 结论说明
    public boolean copyReloc;       // 经 copy relocation 绑定到可执行文件本地副本
    public final List<Candidate> candidates = new ArrayList<>();
    public final List<RelocRef> relocations = new ArrayList<>();

    public String key() {
        return version == null ? symbolName : symbolName + "@" + version;
    }
}
