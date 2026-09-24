package com.symbolcourt.resolve;

import com.symbolcourt.model.*;
import com.symbolcourt.model.Enums.*;

import java.util.*;

public class LinkEngine {

    private final PlatformPolicy policy;
    private LinkResult result;
    private CandidateRegistry reg;
    private int scanPos;
    private final Map<String, InputUnit> memberUnits = new LinkedHashMap<>();

    public LinkEngine(PlatformPolicy policy) {
        this.policy = policy;
    }

    public LinkResult link(List<InputUnit> inputs) {
        result = new LinkResult();
        result.policy = policy;
        reg = new CandidateRegistry(policy);
        for (InputUnit u : inputs) result.units.add(u);

        scanExplicitAndArchives();
        decideSymbols();
        decideRelocations();
        buildDependencyOrder(inputs);
        finishSummary();
        return result;
    }

    private boolean isCorrupt(InputUnit u) {
        if (u.type == InputUnit.UnitType.CORRUPT) return true;
        if (u.elf != null && u.elf.corrupt) return true;
        if (u.type == InputUnit.UnitType.ARCHIVE && u.archive != null && u.archive.corrupt) return true;
        return false;
    }

    private void scanExplicitAndArchives() {
        // 1) explicit (non-archive) objects and shared libraries are always loaded
        for (InputUnit u : result.units) {
            if (u.type == InputUnit.UnitType.ARCHIVE) continue;
            if (isCorrupt(u)) {
                isolate(u);
                continue;
            }
            activate(u, 0);
        }

        // 2) archives are scanned repeatedly (left to right) until a fixed point;
        //    repeated passes naturally resolve circular dependencies between archives.
        boolean progress = true;
        int pass = 0;
        while (progress) {
            pass++;
            progress = false;
            for (InputUnit archive : result.units) {
                if (archive.type != InputUnit.UnitType.ARCHIVE) continue;
                if (isCorrupt(archive)) {
                    isolateArchiveOnce(archive);
                    continue;
                }
                for (ArchiveMember m : archive.archive.members) {
                    InputUnit mu = memberUnits.get(archive.id + "#" + m.name);
                    if (mu != null && mu.extracted) continue;
                    if (m.corrupt || m.elf == null) {
                        Step st = new Step(result.steps.size(), Step.Kind.CORRUPT_SKIP,
                                "隔离损坏 archive 成员", archive.displayName + "(" + m.name + ")");
                        st.facts.add(m.error != null ? m.error : "格式错误，仅隔离该成员");
                        result.steps.add(st);
                        if (m.elf != null) result.isolatedCorrupt.add(m.elf);
                        continue;
                    }
                    mu = ensureMemberUnit(archive, m);
                    if (reg.providesNeededSymbol(mu, policy.extractWeakSatisfies)) {
                        mu.extracted = true;
                        mu.extractStep = result.steps.size();
                        mu.extractReason = "提供当前未满足的引用";
                        activate(mu, pass);
                        Step st = new Step(result.steps.size(), Step.Kind.EXTRACT_MEMBER,
                                "惰性抽取 archive 成员",
                                archive.displayName + "(" + m.name + ") 第 " + pass + " 趟");
                        st.unit = mu.label();
                        st.facts.add("触发符号: " + triggerNames(mu));
                        result.steps.add(st);
                        result.extractionLog.add(archive.displayName + "(" + m.name + ")@pass" + pass);
                        progress = true;
                    }
                }
            }
        }

        // 3) record never-extracted members for the play-through
        for (InputUnit archive : result.units) {
            if (archive.type != InputUnit.UnitType.ARCHIVE || isCorrupt(archive)) continue;
            for (ArchiveMember m : archive.archive.members) {
                InputUnit mu = memberUnits.get(archive.id + "#" + m.name);
                if (mu == null || !mu.extracted) {
                    Step st = new Step(result.steps.size(), Step.Kind.REJECT_CANDIDATE,
                            "archive 成员保持未抽取", archive.displayName + "(" + m.name + ")");
                    st.unit = archive.displayName + "(" + m.name + ")";
                    st.facts.add("没有仍未满足的引用请求它");
                    result.steps.add(st);
                }
            }
        }
    }

    private InputUnit ensureMemberUnit(InputUnit archive, ArchiveMember m) {
        return memberUnits.computeIfAbsent(archive.id + "#" + m.name, k -> {
            InputUnit mu = new InputUnit(k, InputUnit.UnitType.OBJECT, archive.order, m.elf, null);
            mu.archiveMemberName = m.name;
            return mu;
        });
    }

    private String triggerNames(InputUnit mu) {
        List<String> names = new ArrayList<>();
        for (ElfSymbol s : mu.elf.symbols) {
            if (s.isDefined() && s.binding != Binding.LOCAL
                    && s.visibility != Visibility.HIDDEN && s.visibility != Visibility.INTERNAL
                    && reg.isNeeded(s.name, s.versionName, policy.extractWeakSatisfies)) {
                names.add(s.versionedName());
            }
        }
        return names.isEmpty() ? "(引用稍后满足)" : String.join(", ", new LinkedHashSet<>(names));
    }

    private void activate(InputUnit u, int pass) {
        scanPos++;
        reg.currentScanPos = scanPos;
        reg.currentPass = pass;
        reg.registerGroups(u, result.steps);
        reg.addDefinitions(u);
        reg.addReferences(u);
        Step st = new Step(result.steps.size(), Step.Kind.SCAN_OBJECT, "载入对象", u.label());
        st.unit = u.label();
        if (u.elf != null) {
            st.facts.add(u.elf.kind + (u.elf.soname != null ? " soname=" + u.elf.soname : ""));
        }
        result.steps.add(st);
    }

    private void isolate(InputUnit u) {
        if (u.elf != null) {
            result.isolatedCorrupt.add(u.elf);
            Step st = new Step(result.steps.size(), Step.Kind.CORRUPT_SKIP,
                    "隔离损坏对象", u.label());
            st.facts.add(u.elf.error != null ? u.elf.error : "格式校验失败，仅隔离自身");
            result.steps.add(st);
        }
    }

    private final Set<String> isolatedArchives = new HashSet<>();

    private void isolateArchiveOnce(InputUnit archive) {
        if (!isolatedArchives.add(archive.id)) return;
        ParsedElf synthetic = new ParsedElf();
        synthetic.displayName = archive.label();
        synthetic.corrupt = true;
        synthetic.error = archive.archive != null ? archive.archive.error : "archive 损坏";
        result.isolatedCorrupt.add(synthetic);
        Step st = new Step(result.steps.size(), Step.Kind.CORRUPT_SKIP,
                "隔离损坏 archive", archive.label());
        st.facts.add(synthetic.error);
        result.steps.add(st);
    }

    private void decideSymbols() {
        new SymbolDecider(policy, reg, result).decide();
    }

    private void decideRelocations() {
        List<InputUnit> active = new ArrayList<>();
        for (InputUnit u : result.units) {
            if (u.type == InputUnit.UnitType.ARCHIVE) continue;
            if (u.elf != null && !u.elf.corrupt) active.add(u);
        }
        active.addAll(memberUnits.values().stream().filter(m -> m.extracted).toList());
        new RelocationDecider(policy, reg, result, active).decide();
    }

    private void buildDependencyOrder(List<InputUnit> inputs) {
        for (InputUnit u : result.units) {
            if (u.type == InputUnit.UnitType.ARCHIVE) continue;
            if (u.elf == null || u.elf.corrupt) continue;
            String line = u.label();
            if (u.elf.soname != null) line += " [soname=" + u.elf.soname + "]";
            if (!u.elf.needed.isEmpty()) line += " -> " + String.join(", ", u.elf.needed);
            result.dependencyOrder.add(line);
        }
        for (InputUnit archive : result.units) {
            if (archive.type != InputUnit.UnitType.ARCHIVE) continue;
            for (ArchiveMember m : archive.archive.members) {
                InputUnit mu = memberUnits.get(archive.id + "#" + m.name);
                if (mu != null && mu.extracted && m.elf != null && !m.elf.needed.isEmpty()) {
                    result.dependencyOrder.add(archive.displayName + "(" + m.name + ")"
                            + " -> " + String.join(", ", m.elf.needed));
                }
            }
        }
    }

    private void finishSummary() {
        long resolved = result.resolutions.values().stream()
                .filter(r -> r.status == ResolutionStatus.RESOLVED
                        || r.status == ResolutionStatus.COMMON_BLOCK
                        || r.status == ResolutionStatus.COPY_GENERATED).count();
        long ambiguous = result.resolutions.values().stream()
                .filter(r -> r.status == ResolutionStatus.AMBIGUOUS).count();
        long mult = result.resolutions.values().stream()
                .filter(r -> r.status == ResolutionStatus.MULTIPLE_DEFINITION).count();
        long undef = result.resolutions.values().stream()
                .filter(r -> r.status == ResolutionStatus.UNDEFINED
                        || r.status == ResolutionStatus.UNSATISFIED_VERSION).count();
        result.summary = String.format(
                "候选键 %d，已满足 %d，歧义 %d，重复定义 %d，未定义/版本缺失 %d，隔离损坏 %d",
                result.resolutions.size(), resolved, ambiguous, mult, undef,
                result.isolatedCorrupt.size());
    }
}
