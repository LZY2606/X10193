package court.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 方案的输入描述（加载顺序就是 orderedUploadIds 的顺序；不做目录扫描）。
 */
public final class SchemeInput {
    public String name;
    public Policy policy = Policy.STATIC_LD;
    public final List<String> orderedUploadIds = new ArrayList<>();
}
