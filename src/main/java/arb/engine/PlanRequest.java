package arb.engine;

import arb.model.Enums;

import java.util.ArrayList;
import java.util.List;

public class PlanRequest {
    private String label;
    private Enums.LinkMode mode = Enums.LinkMode.STATIC;
    private String platformPolicy = "gnu";
    private List<Entry> entries = new ArrayList<>();

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Enums.LinkMode getMode() {
        return mode;
    }

    public void setMode(Enums.LinkMode mode) {
        this.mode = mode;
    }

    public String getPlatformPolicy() {
        return platformPolicy;
    }

    public void setPlatformPolicy(String platformPolicy) {
        this.platformPolicy = platformPolicy;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public static class Entry {
        private String name;
        private byte[] data;
        private boolean asNeeded;

        public Entry() {
        }

        public Entry(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public boolean isAsNeeded() {
            return asNeeded;
        }

        public void setAsNeeded(boolean asNeeded) {
            this.asNeeded = asNeeded;
        }
    }
}
