package tribunal.engine;

/** 平台裁决策略。加载顺序与策略变化都会生成新方案版本，旧版本结果固化不漂移。 */
public enum Policy {
    /** 经典静态链接：同强度候选由显式加载顺序中的前者获胜。 */
    FIRST_WINS,
    /** 严格：存在多个同等候选时保留歧义，禁止用文件系统遍历次序决定。 */
    STRICT_AMBIGUOUS,
    /** System V 动态语义：搜索顺序中第一个满足的定义获胜。 */
    SYSV_DYNAMIC
}
