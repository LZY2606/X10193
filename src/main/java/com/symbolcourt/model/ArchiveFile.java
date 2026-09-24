package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArchiveFile {
    public String displayName;
    public boolean thin;
    public boolean corrupt;
    public String error;
    public List<ArchiveMember> members = new ArrayList<>();
    public final Map<String, List<Integer>> symbolIndex = new LinkedHashMap<>();
}
