package com.court.model;

public record VersionDef(int index, String name, boolean nonDefault, boolean base) {
    public String decorated() {
        return name + (base ? " (base)" : "") + (nonDefault ? " [non-default]" : " [default]");
    }
}
