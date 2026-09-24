package arb.engine;

import java.util.ArrayList;
import java.util.List;

public class ObjectView {
    private String name;
    private String member;
    private String kind;
    private int order;
    private boolean extracted;
    private boolean archive;
    private String soname;
    private List<String> needed = new ArrayList<>();
    private List<String> provides = new ArrayList<>();
    private List<String> undefined = new ArrayList<>();
    private List<GroupView> groups = new ArrayList<>();
    private String loadError;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isExtracted() {
        return extracted;
    }

    public void setExtracted(boolean extracted) {
        this.extracted = extracted;
    }

    public boolean isArchive() {
        return archive;
    }

    public void setArchive(boolean archive) {
        this.archive = archive;
    }

    public String getSoname() {
        return soname;
    }

    public void setSoname(String soname) {
        this.soname = soname;
    }

    public List<String> getNeeded() {
        return needed;
    }

    public void setNeeded(List<String> needed) {
        this.needed = needed;
    }

    public List<String> getProvides() {
        return provides;
    }

    public void setProvides(List<String> provides) {
        this.provides = provides;
    }

    public List<String> getUndefined() {
        return undefined;
    }

    public void setUndefined(List<String> undefined) {
        this.undefined = undefined;
    }

    public List<GroupView> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupView> groups) {
        this.groups = groups;
    }

    public String getLoadError() {
        return loadError;
    }

    public void setLoadError(String loadError) {
        this.loadError = loadError;
    }

    public static class GroupView {
        private String signature;
        private boolean comdat;
        private List<String> members = new ArrayList<>();
        private boolean discarded;
        private String discardReason;

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public boolean isComdat() {
            return comdat;
        }

        public void setComdat(boolean comdat) {
            this.comdat = comdat;
        }

        public List<String> getMembers() {
            return members;
        }

        public void setMembers(List<String> members) {
            this.members = members;
        }

        public boolean isDiscarded() {
            return discarded;
        }

        public void setDiscarded(boolean discarded) {
            this.discarded = discarded;
        }

        public String getDiscardReason() {
            return discardReason;
        }

        public void setDiscardReason(String discardReason) {
            this.discardReason = discardReason;
        }
    }
}
