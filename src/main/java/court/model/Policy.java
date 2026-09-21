package court.model;

/**
 * 目标平台策略。切换策略会形成新的方案版本，历史方案结果不漂移（结果在创建时快照）。
 *
 * <ul>
 *   <li>STATIC_LD：传统静态链接。archive 惰性抽取；strong/common/weak 按 gABI 状态表裁决；
 *       共享库定义只在没有普通定义时满足引用。</li>
 *   <li>RTLD_GLOBAL：dlopen/dynamic 语义。archive 不抽取；按 BFS 依赖顺序 global scope
 *       先到先得；weak 也绑定；PROTECTED 内部引用保留到本对象。</li>
 * </ul>
 */
public enum Policy {
    STATIC_LD("静态链接 (ld 风格)"),
    RTLD_GLOBAL("动态加载 (ld.so 全局作用域)");

    private final String label;

    Policy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
