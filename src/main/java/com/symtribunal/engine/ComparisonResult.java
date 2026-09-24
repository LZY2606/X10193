package com.symtribunal.engine;

import java.util.ArrayList;
import java.util.List;

public class ComparisonResult {
    public String schemeA;
    public String schemeB;
    public List<Change> providerChanged = new ArrayList<>();
    public List<Change> addressOnly = new ArrayList<>();
    public List<Change> resolvedDifferently = new ArrayList<>();
    public List<Change> onlyInA = new ArrayList<>();
    public List<Change> onlyInB = new ArrayList<>();

    public static class Change {
        public String key;
        public String unit;
        public String symbol;
        public String requiredVersion;
        public String fromProvider;
        public String toProvider;
        public Long fromAddress;
        public Long toAddress;
        public String fromStatus;
        public String toStatus;
        public String note;
    }
}
