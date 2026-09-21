package court.model;

/**
 * 对象依赖图的一条边（DT_NEEDED）。resolved=false 时说明导入集合中找不到该名字。
 */
public final class DependencyEdge {
    public final String fromUnitId;
    public final String needed; // DT_NEEDED 原文
    public final String toUnitId; // 匹配到的单元，未匹配为 null
    public final boolean resolved;

    public DependencyEdge(String fromUnitId, String needed, String toUnitId, boolean resolved) {
        this.fromUnitId = fromUnitId;
        this.needed = needed;
        this.toUnitId = toUnitId;
        this.resolved = resolved;
    }
}
