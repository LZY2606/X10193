package court.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可逐步播放的一步（archive 抽取、符号表更新、COMDAT 去重、依赖展开、引用裁决）。
 * snapshot 为该步完成后“当前符号表”的只读视图（符号名 -> 提供者描述），用于动画。
 */
public final class Step {
    public final int index;
    public final String phase; // DEPENDENCY / COMDAT / ARCHIVE_SCAN / SYMBOL_UPDATE / RESOLVE
    public final String title;
    public final String description;
    public final String focusUnitId;
    public final String focusSymbol;
    public final Map<String, String> symbolTableSnapshot;

    public Step(int index, String phase, String title, String description,
                String focusUnitId, String focusSymbol, Map<String, String> snapshot) {
        this.index = index;
        this.phase = phase;
        this.title = title;
        this.description = description;
        this.focusUnitId = focusUnitId;
        this.focusSymbol = focusSymbol;
        this.symbolTableSnapshot = snapshot == null ? new LinkedHashMap<>() : snapshot;
    }
}
