package com.symbolcourt.resolve;

import com.symbolcourt.model.ParsedElf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkResult {
    public PlatformPolicy policy;
    public final List<InputUnit> units = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    public final Map<SymbolKey, SymbolResolution> resolutions = new LinkedHashMap<>();
    public final List<RelocationDecision> relocations = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    public final List<String> dependencyOrder = new ArrayList<>();
    public final List<String> extractionLog = new ArrayList<>();
    public final List<ParsedElf> isolatedCorrupt = new ArrayList<>();
    public String summary;
}
