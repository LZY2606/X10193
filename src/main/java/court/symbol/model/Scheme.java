package court.symbol.model;

import court.symbol.elf.ParsedBinary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Scheme {
    public String id;
    public String name;
    public PlatformPolicy policy = PlatformPolicy.GNU_LOAD_ORDER;
    public final List<Unit> explicitUnits = new ArrayList<>();
    public final List<Unit> activeUnits = new ArrayList<>();
    public final List<Unit> availableMembers = new ArrayList<>();
    public final List<ExtractionEvent> extractionEvents = new ArrayList<>();
    public final List<ResolutionDecision> decisions = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public Map<String, ParsedBinary> binaries;
}
