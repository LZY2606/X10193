package court.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 COMDAT 签名的去重结论：保留方与所有丢弃方（丢弃原因显式记录）。
 */
public final class ComdatDecision {
    public final String signature;
    public static final class Instance {
        public final String unitId;
        public final String unitName;
        public final int sectionIndex;
        public final boolean kept;
        public final String reason;

        public Instance(String unitId, String unitName, int sectionIndex, boolean kept, String reason) {
            this.unitId = unitId;
            this.unitName = unitName;
            this.sectionIndex = sectionIndex;
            this.kept = kept;
            this.reason = reason;
        }
    }

    public final List<Instance> instances = new ArrayList<>();

    public ComdatDecision(String signature) {
        this.signature = signature;
    }
}
