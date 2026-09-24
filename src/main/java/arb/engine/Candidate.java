package arb.engine;

import arb.model.Enums;

public class Candidate {
    private String provider;
    private String member;
    private int order;
    private Enums.Binding binding;
    private Enums.SymbolType type;
    private Enums.Visibility visibility;
    private String version;
    private boolean defaultVersion;
    private long size;
    private long address;
    private boolean comdatDuplicate;
    private boolean common;
    private boolean copy;
    private boolean selected;
    private String elimination;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Enums.Binding getBinding() {
        return binding;
    }

    public void setBinding(Enums.Binding binding) {
        this.binding = binding;
    }

    public Enums.SymbolType getType() {
        return type;
    }

    public void setType(Enums.SymbolType type) {
        this.type = type;
    }

    public Enums.Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Enums.Visibility visibility) {
        this.visibility = visibility;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(boolean defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getAddress() {
        return address;
    }

    public void setAddress(long address) {
        this.address = address;
    }

    public boolean isComdatDuplicate() {
        return comdatDuplicate;
    }

    public void setComdatDuplicate(boolean comdatDuplicate) {
        this.comdatDuplicate = comdatDuplicate;
    }

    public boolean isCommon() {
        return common;
    }

    public void setCommon(boolean common) {
        this.common = common;
    }

    public boolean isCopy() {
        return copy;
    }

    public void setCopy(boolean copy) {
        this.copy = copy;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getElimination() {
        return elimination;
    }

    public void setElimination(String elimination) {
        this.elimination = elimination;
    }
}
