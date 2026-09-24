package arb.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PlanResult {
    private Long id;
    private String planKey;
    private String label;
    private String mode;
    private String platformPolicy;
    private Instant createdAt;
    private List<ObjectView> objects = new ArrayList<>();
    private List<Resolution> resolutions = new ArrayList<>();
    private List<PlanStep> steps = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlanKey() {
        return planKey;
    }

    public void setPlanKey(String planKey) {
        this.planKey = planKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPlatformPolicy() {
        return platformPolicy;
    }

    public void setPlatformPolicy(String platformPolicy) {
        this.platformPolicy = platformPolicy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<ObjectView> getObjects() {
        return objects;
    }

    public void setObjects(List<ObjectView> objects) {
        this.objects = objects;
    }

    public List<Resolution> getResolutions() {
        return resolutions;
    }

    public void setResolutions(List<Resolution> resolutions) {
        this.resolutions = resolutions;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PlanStep> steps) {
        this.steps = steps;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
