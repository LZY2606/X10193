package com.tribunal.core;

import java.util.ArrayList;
import java.util.List;

/** 一次裁决的完整输出。 */
public class AdjudicationResult {
    public final List<LoadedObject> objects = new ArrayList<>();
    public final List<Verdict> verdicts = new ArrayList<>();
    public final List<ResolutionEvent> events = new ArrayList<>();
    public final List<Dependency> dependencies = new ArrayList<>();
    public final List<CorruptObject> corrupt = new ArrayList<>();

    public static class Dependency {
        public String from;
        public String to;
        public String kind;     // PROVIDES（符号供给）或 NEEDED（DT_NEEDED 声明）
    }

    public static class CorruptObject {
        public String name;
        public String reason;
    }
}
