package com.court.model;

public record VersionReq(String neededFrom, String name, boolean nonDefault, int otherIndex) {
    public String decorated() { return name + (nonDefault ? "@" : "@@") + " (from " + neededFrom + ")"; }
}
