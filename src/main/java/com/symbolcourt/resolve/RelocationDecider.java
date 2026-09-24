package com.symbolcourt.resolve;

import com.symbolcourt.model.*;
import com.symbolcourt.model.Enums.*;

import java.util.*;

public class RelocationDecider {

    private final PlatformPolicy policy;
    private final CandidateRegistry reg;
    private final LinkResult result;
    private final List<InputUnit> active;

    public RelocationDecider(PlatformPolicy policy, CandidateRegistry reg, LinkResult result,
                             List<InputUnit> active) {
        this.policy = policy;
        this.reg = reg;
        this.result = result;
        this.active = active;
    }

    public void decide() {
        for (InputUnit u : active) processUnit(u);
    }

    private void processUnit(InputUnit u) {
        for (ElfRelocation rel : u.elf.relocations) {
            RelocationDecision d = new RelocationDecision();
            d.unit = u.label();
            d.sectionName = rel.sectionName;
            d.targetSection = rel.targetSectionName;
            d.offset = rel.offset;
            d.typeName = rel.typeName;
            d.kind = rel.kind;
            d.symbolName = rel.symbolName;
            d.addend = rel.addend;
            fillRequestedVersion(u, rel, d);

            if (rel.kind == RelocKind.RELATIVE) {
                d.status = "RELATIVE";
                d.satisfied = true;
                d.explanation = "相对重定位基于加载基址，无需符号提供者";
            } else if (rel.kind == RelocKind.IRELATIVE) {
                d.status = "IRELATIVE";
                d.satisfied = true;
                d.explanation = "IFUNC 间接重定位，目标由 resolver 给出";
            } else if (rel.symbolName == null || rel.symbolName.isEmpty()) {
                d.status = "NO_SYMBOL";
                d.satisfied = true;
                d.explanation = "重定位不引用命名符号";
            } else {
                bind(u, rel, d);
            }
            result.relocations.add(d);
        }
    }

    private void fillRequestedVersion(InputUnit u, ElfRelocation rel, RelocationDecision d) {
        List<ElfSymbol> table = rel.dynamic ? u.elf.dynamicSymbols : rel.symIndex < u.elf.dynamicSymbols.size()
                && rel.dynamic ? u.elf.dynamicSymbols : u.elf.symbols;
        if (rel.dynamic) table = u.elf.dynamicSymbols;
        if (rel.symIndex >= 0 && rel.symIndex < table.size()) {
            ElfSymbol s = table.get(rel.symIndex);
            d.requestedVersion = s.versionName;
        }
    }

    private Candidate localCandidate(InputUnit u, String name, String requestedVersion) {
        Map<SymbolKey, Candidate> locals = reg.local.get(u.id);
        if (locals == null) return null;
        Candidate c = locals.get(new SymbolKey(name, requestedVersion));
        if (c != null) return c;
        if (requestedVersion == null) return locals.get(new SymbolKey(name, null));
        for (Candidate cand : locals.values()) {
            if (cand.symbol.name.equals(name) && cand.symbol.versionDefault) return cand;
        }
        return null;
    }

    private void bind(InputUnit u, ElfRelocation rel, RelocationDecision d) {
        String name = d.symbolName;
        String requested = d.requestedVersion;

        Candidate local = localCandidate(u, name, requested);
        if (local != null) {
            d.provider = local.unitLabel();
            d.boundValue = local.value;
            d.satisfied = true;
            d.versionMatched = requested == null || requested.equals(local.key.version)
                    || (local.symbol.versionDefault && requested != null);
            d.status = "LOCAL";
            d.explanation = "LOCAL/HIDDEN 定义在本对象内部直接绑定，不参与跨对象覆盖";
            return;
        }

        List<Candidate> exact = reg.global.getOrDefault(new SymbolKey(name, requested), List.of());
        for (Candidate c : exact) {
            if (c.protectedVis && c.unit.id.equals(u.id)) {
                d.provider = c.unitLabel();
                d.boundValue = c.value;
                d.satisfied = true;
                d.status = "PROTECTED_LOCAL";
                d.explanation = "PROTECTED 数据从定义对象内部解析为本对象地址";
                result.relocations.add(d);
                return;
            }
        }

        if (rel.kind == RelocKind.COPY) {
            handleCopy(u, rel, d, name, requested, exact);
            return;
        }

        SymbolResolution res = result.resolutions.get(new SymbolKey(name, requested));
        if (res == null) res = findByName(name);

        if (res == null) {
            d.status = "UNRESOLVED";
            d.satisfied = false;
            d.explanation = "裁决表中没有该符号";
            result.relocations.add(d);
            return;
        }

        Candidate chosen = res.chosen;
        switch (res.status) {
            case AMBIGUOUS -> {
                d.status = "AMBIGUOUS";
                d.satisfied = false;
                d.explanation = res.note;
            }
            case MULTIPLE_DEFINITION -> {
                d.status = "MULTIPLE_DEFINITION";
                d.satisfied = false;
                d.explanation = res.note;
            }
            case UNSATISFIED_VERSION -> {
                d.status = "UNSATISFIED_VERSION";
                d.satisfied = false;
                d.versionMatched = false;
                d.explanation = res.note;
                if (res.unsatisfiedVersionFoundAt != null) d.provider = res.unsatisfiedVersionFoundAt;
            }
            case UNDEFINED -> {
                d.status = res.weakReference ? "WEAK_NULL" : "UNDEFINED";
                d.satisfied = res.weakReference;
                d.explanation = res.weakReference ? "弱引用绑定为 0" : "无提供者";
            }
            default -> {
                if (chosen == null) {
                    d.status = "UNRESOLVED";
                    d.satisfied = false;
                    d.explanation = "无可用提供者";
                    break;
                }
                if (chosen.protectedVis && chosen.shared && rel.kind != RelocKind.JUMP_SLOT) {
                    switch (policy.protectedDataPolicy) {
                        case ERROR -> {
                            d.status = "PROTECTED_DATA_ERROR";
                            d.satisfied = false;
                            d.explanation = "不能对其他对象的 PROTECTED 数据符号做非 COPY 介入重定位";
                            result.errors.add(name + ": cannot relocate protected data");
                            break;
                        }
                        case RESOLVE_LOCAL -> {
                            d.status = "PROTECTED_RESOLVE_LOCAL";
                            d.satisfied = true;
                            d.provider = chosen.unitLabel();
                            d.boundValue = chosen.value;
                            d.explanation = "按平台策略，PROTECTED 数据解析到定义处地址";
                            break;
                        }
                        case COPY -> handleCopy(u, rel, d, name, requested, exact);
                    }
                    break;
                }
                d.provider = chosen.unitLabel();
                d.boundValue = chosen.value;
                d.satisfied = true;
                d.versionMatched = requested == null
                        || requested.equals(chosen.key.version)
                        || (chosen.symbol.versionDefault);
                if (rel.kind == RelocKind.JUMP_SLOT) {
                    d.status = "JUMP_SLOT_LAZY";
                    d.explanation = "PLT 入口，首次调用时解析到 " + chosen.unitLabel();
                } else if (rel.kind == RelocKind.GLOB_DAT) {
                    d.status = "GLOB_DAT";
                    d.explanation = "GOT 数据项加载时绑定到 " + chosen.unitLabel();
                } else {
                    d.status = chosen.common ? "COMMON_BLOCK" : "BOUND";
                    d.explanation = "普通重定位引用 " + chosen.unitLabel()
                            + (chosen.common ? " 的公共块" : " 的定义");
                }
            }
        }
        result.relocations.add(d);
    }

    private SymbolResolution findByName(String name) {
        for (SymbolResolution r : result.resolutions.values()) {
            if (r.name.equals(name)) return r;
        }
        return null;
    }

    private void handleCopy(InputUnit u, ElfRelocation rel, RelocationDecision d,
                            String name, String requested, List<Candidate> exact) {
        Candidate source = null;
        for (Candidate c : exact) {
            if (c.shared && c.symbol.type == SymType.OBJECT) {
                source = c;
                break;
            }
        }
        if (source == null) {
            for (Candidate c : reg.byNameGlobal.getOrDefault(name, List.of())) {
                if (c.shared && (requested == null || requested.equals(c.key.version))) {
                    source = c;
                    break;
                }
            }
        }
        if (source == null) {
            d.status = "COPY_NO_SOURCE";
            d.satisfied = false;
            d.explanation = "COPY 重定位找不到共享库里的数据源符号";
            result.relocations.add(d);
            return;
        }
        if (source.protectedVis) {
            d.status = "COPY_PROTECTED_REJECTED";
            d.satisfied = false;
            d.explanation = "COPY 重定位不能作用于 PROTECTED 数据（" + source.unitLabel() + "）";
            result.errors.add(name + ": copy relocation against protected symbol");
            result.relocations.add(d);
            return;
        }
        d.provider = source.unitLabel();
        d.boundValue = 0L;
        d.satisfied = true;
        d.versionMatched = requested == null || requested.equals(source.key.version);
        d.status = "COPY";
        d.explanation = "在可执行文件生成副本（" + source.size + " 字节），共享库 "
                + source.unitLabel() + " 中同名对象被重定向到该副本（独立裁决模式，不走普通覆盖）";
        Step st = new Step(result.steps.size(), Step.Kind.COPY_RELOC,
                "copy relocation 建立副本", name);
        st.unit = u.label();
        st.facts.add("数据源: " + source.unitLabel());
        st.facts.add("字节数: " + source.size);
        result.steps.add(st);
        result.relocations.add(d);
    }
}
