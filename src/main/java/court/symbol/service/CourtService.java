package court.symbol.service;

import court.symbol.db.ObjectRecord;
import court.symbol.db.ObjectRepository;
import court.symbol.db.PlanRepository;
import court.symbol.engine.Resolver;
import court.symbol.model.InputObject;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.ResolutionReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CourtService {

    private final ObjectRepository objects;
    private final PlanRepository plans;

    public CourtService(ObjectRepository objects, PlanRepository plans) {
        this.objects = objects;
        this.plans = plans;
    }

    public record CreatedVersion(String planId, int version, ResolutionReport report) {
    }

    /**
     * Creates a plan (first call for a name) or appends an immutable version.
     * Load order is taken from entries[].order, ascending.
     */
    public CreatedVersion createVersion(Dtos.CreatePlanRequest req) {
        long now = System.currentTimeMillis();
        List<InputObject> inputs = new ArrayList<>();
        req.entries().stream()
                .sorted(java.util.Comparator.comparingInt(Dtos.EntrySpec::order))
                .forEach(e -> {
                    ObjectRecord rec = objects.fetch(e.sha256());
                    if (rec == null) {
                        throw new IllegalArgumentException("未知对象 sha256=" + e.sha256());
                    }
                    inputs.add(new InputObject(e.order(), rec.sha256(),
                            e.label() != null ? e.label() : rec.filename(),
                            rec.kind().equals("ARCHIVE") ? "ARCHIVE"
                                    : rec.kind().equals("ELF") ? guessRole(rec.content())
                                    : "UNKNOWN",
                            rec.content()));
                });
        PlatformPolicy policy = new PlatformPolicy(
                "DYNAMIC".equals(req.linkModel())
                        ? PlatformPolicy.DYNAMIC : PlatformPolicy.STATIC,
                "REPORT_AMBIGUITY".equals(req.tiePolicy())
                        ? PlatformPolicy.AMBIG : PlatformPolicy.FIRST,
                req.allowInterposition());

        String planId = findOrCreatePlanId(req.name(), req.targetPlatform(), now);
        int version = plans.nextVersion(planId);

        Resolver resolver = new Resolver(inputs, policy,
                req.targetPlatform() == null ? "x86-64" : req.targetPlatform(),
                planId, version, now);
        ResolutionReport report = resolver.resolve();
        plans.insertVersion(planId, version, policy, req, report);
        return new CreatedVersion(planId, version, report);
    }

    private String guessRole(byte[] content) {
        // Distinguishing ET_REL vs ET_DYN properly happens inside the engine;
        // mark all ELF as OBJECT, the engine sniffs type per file.
        return "OBJECT";
    }

    private String findOrCreatePlanId(String name, String targetPlatform, long now) {
        for (Dtos.PlanSummary s : plans.listPlans()) {
            if (s.name().equals(name)) {
                return s.id();
            }
        }
        String id = "plan-" + UUID.randomUUID();
        plans.insertPlan(id, name, targetPlatform == null ? "x86-64" : targetPlatform, now);
        return id;
    }

    public ResolutionReport report(String planId, int version) {
        return plans.loadReport(planId, version);
    }

    public List<Dtos.PlanSummary> plans() {
        return plans.listPlans();
    }

    public List<Dtos.VersionSummary> versions(String planId) {
        return plans.listVersions(planId);
    }
}
