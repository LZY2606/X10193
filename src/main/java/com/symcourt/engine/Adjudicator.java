package com.symcourt.engine;

import com.symcourt.elf.Archive;
import com.symcourt.elf.ElfFile;
import com.symcourt.engine.EngineModel.CandidateView;
import com.symcourt.engine.EngineModel.CompareEntry;
import com.symcourt.engine.EngineModel.GroupEvent;
import com.symcourt.engine.EngineModel.ObjectView;
import com.symcourt.engine.EngineModel.RelocView;
import com.symcourt.engine.EngineModel.Result;
import com.symcourt.engine.EngineModel.StepView;
import com.symcourt.engine.EngineModel.Verdict;
import com.symcourt.engine.EngineModel.VersionNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The symbol court. Resolves every undefined reference against the loaded
 * objects without ever invoking a system linker. Every decision keeps its
 * candidate list and elimination reasons; ties stay ambiguous instead of
 * being decided by traversal order.
 */
public final class Adjudicator {

    public sealed interface LoadUnit permits ObjectUnit, ArchiveUnit {
        String name();
    }

    public record ObjectUnit(String name, ElfFile elf) implements LoadUnit { }

    public record ArchiveUnit(String name, List<Archive.Member> members) implements LoadUnit { }

    private static final class Obj {
        final String name;
        final ElfFile elf;
        final int order;
        int sub;
        boolean extracted = true;
        final Set<Integer> discardedSections = new LinkedHashSet<>();
        final List<Def> defs = new ArrayList<>();

        Obj(String name, ElfFile elf, int order) {
            this.name = name;
            this.elf = elf;
            this.order = order;
        }

        long base() {
            return (long) order * 0x100000L + (long) sub * 0x1000L;
        }
    }

    private static final class Def {
        final Obj obj;
        final ElfFile.Symbol sym;
        final String version;
        final boolean defaultVersion;

        Def(Obj obj, ElfFile.Symbol sym, String version, boolean defaultVersion) {
            this.obj = obj;
            this.sym = sym;
            this.version = version;
            this.defaultVersion = defaultVersion;
        }

        boolean discarded() {
            return obj.discardedSections.contains(sym.shndx);
        }
    }

    private static final class Ref {
        final Obj obj;
        final ElfFile.Symbol sym;
        final String version;

        Ref(Obj obj, ElfFile.Symbol sym, String version) {
            this.obj = obj;
            this.sym = sym;
            this.version = version;
        }
    }

    private final PlatformPolicy policy;
    private final List<Obj> objs = new ArrayList<>();
    private final List<Obj> archivePool = new ArrayList<>();
    private final List<StepView> steps = new ArrayList<>();
    private final List<GroupEvent> groupEvents = new ArrayList<>();
    private int stepSeq;
    private long commonAlloc = 0xB00000L;
    private long copyAlloc = 0xC00000L;

    public Adjudicator(PlatformPolicy policy) {
        this.policy = policy;
    }

    public Result adjudicate(List<LoadUnit> units) {
        int order = 0;
        for (LoadUnit u : units) {
            if (u instanceof ObjectUnit o) {
                Obj obj = new Obj(o.name(), o.elf(), order++);
                objs.add(obj);
                step("LOAD", "加载对象 " + obj.name);
            } else if (u instanceof ArchiveUnit a) {
                int archiveOrder = order++;
                step("LOAD", "加载归档 " + a.name());
                int sub = 0;
                for (Archive.Member m : a.members()) {
                    ElfFile elf;
                    try {
                        elf = ElfFile.parse(m.data);
                    } catch (RuntimeException e) {
                        step("CORRUPT", "归档成员 " + m.name + " 解析失败，已隔离: " + e.getMessage());
                        continue;
                    }
                    Obj member = new Obj(a.name() + "(" + m.name + ")", elf, archiveOrder);
                    member.sub = sub++;
                    member.extracted = false;
                    archivePool.add(member);
                }
            }
        }
        extractArchiveMembers();
        runComdatDedup();
        buildDefs();
        List<Ref> refs = collectRefs();
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Verdict> byKey = new LinkedHashMap<>();
        for (Ref r : refs) {
            Verdict v = resolve(r);
            verdicts.add(v);
            byKey.put(r.obj.name + "" + r.sym.name + "" + (r.version == null ? "" : r.version), v);
        }
        List<RelocView> relocs = buildRelocViews(byKey);
        return new Result(objectViews(verdicts), verdicts, relocs, steps, versionGraph(), groupEvents);
    }

    private void step(String kind, String message) {
        steps.add(new StepView(stepSeq++, kind, message));
    }

    private void extractArchiveMembers() {
        if (!policy.lazyArchive()) {
            for (Obj m : archivePool) {
                m.extracted = true;
                objs.add(m);
                step("EXTRACT", "非惰性策略：抽出成员 " + m.name);
            }
            archivePool.clear();
            sortObjs();
            return;
        }
        boolean progress = true;
        while (progress) {
            progress = false;
            Set<String> needed = undefinedNames();
            for (Obj m : archivePool) {
                if (m.extracted) {
                    continue;
                }
                Set<String> provides = definedNames(m);
                provides.retainAll(needed);
                if (!provides.isEmpty()) {
                    m.extracted = true;
                    objs.add(m);
                    step("EXTRACT", "惰性抽出 " + m.name + " 以满足 " + new TreeSet<>(provides));
                    progress = true;
                }
            }
            sortObjs();
        }
        archivePool.removeIf(o -> o.extracted);
    }

    private void sortObjs() {
        objs.sort(Comparator.comparingInt((Obj o) -> o.order).thenComparingInt(o -> o.sub));
    }

    private Set<String> undefinedNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Obj o : objs) {
            for (ElfFile.Symbol s : primaryTable(o.elf)) {
                if (s.isUndefined() && !s.isLocal() && !s.name.isEmpty()) {
                    names.add(s.name);
                }
            }
        }
        return names;
    }

    private Set<String> definedNames(Obj o) {
        Set<String> names = new LinkedHashSet<>();
        for (ElfFile.Symbol s : primaryTable(o.elf)) {
            if (!s.isUndefined() && !s.isLocal() && !s.name.isEmpty()) {
                names.add(s.name);
            }
        }
        return names;
    }

    private static List<ElfFile.Symbol> primaryTable(ElfFile elf) {
        return elf.dynsym().isEmpty() ? elf.symtab() : elf.dynsym();
    }

    private void runComdatDedup() {
        Map<String, String> kept = new LinkedHashMap<>();
        for (Obj o : objs) {
            for (ElfFile.Group g : o.elf.groups()) {
                if (!g.comdat) {
                    continue;
                }
                String owner = kept.get(g.signature);
                if (owner == null) {
                    kept.put(g.signature, o.name);
                    step("GROUP", "保留 COMDAT 组 " + g.signature + " 于 " + o.name);
                } else {
                    o.discardedSections.addAll(g.sectionIndices);
                    groupEvents.add(new GroupEvent(g.signature, owner, o.name));
                    step("GROUP", "丢弃 " + o.name + " 中重复的 COMDAT 组 " + g.signature);
                }
            }
        }
    }

    private void buildDefs() {
        for (Obj o : objs) {
            boolean dyn = !o.elf.dynsym().isEmpty();
            List<ElfFile.Symbol> table = primaryTable(o.elf);
            for (ElfFile.Symbol s : table) {
                if (s.isUndefined() || s.name.isEmpty()) {
                    continue;
                }
                String version = null;
                boolean defVer = false;
                if (dyn) {
                    ElfFile.VersionRef vr = o.elf.dynsymVersion(s.index);
                    if (vr != null) {
                        version = vr.name;
                        defVer = !vr.hidden;
                    }
                }
                o.defs.add(new Def(o, s, version, defVer));
            }
        }
    }

    private List<Ref> collectRefs() {
        List<Ref> refs = new ArrayList<>();
        for (Obj o : objs) {
            boolean dyn = !o.elf.dynsym().isEmpty();
            for (ElfFile.Symbol s : primaryTable(o.elf)) {
                if (!s.isUndefined() || s.isLocal() || s.name.isEmpty()) {
                    continue;
                }
                String version = null;
                if (dyn) {
                    ElfFile.VersionRef vr = o.elf.dynsymVersion(s.index);
                    if (vr != null) {
                        version = vr.name;
                    }
                }
                refs.add(new Ref(o, s, version));
            }
        }
        return refs;
    }

    private Verdict resolve(Ref r) {
        List<Cand> cands = new ArrayList<>();
        boolean sawVersionMismatch = false;
        for (Obj o : objs) {
            for (Def d : o.defs) {
                if (!d.sym.name.equals(r.sym.name)) {
                    continue;
                }
                Cand c = new Cand(d);
                cands.add(c);
                if (d.discarded()) {
                    c.eliminate("COMDAT 组被去重丢弃");
                } else if (d.sym.isLocal() && d.obj != r.obj) {
                    c.eliminate("local 符号不跨对象");
                } else if (hiddenOutside(d, r)) {
                    c.eliminate("hidden/internal 不导出");
                } else if (r.version != null
                        && (d.version == null || !d.version.equals(r.version))) {
                    sawVersionMismatch = true;
                    c.eliminate("版本不匹配，需要 " + r.version);
                } else if (r.version == null && d.version != null && !d.defaultVersion) {
                    c.eliminate("非默认版本 @" + d.version + " 不满足未版本化引用");
                }
            }
        }
        for (Obj m : archivePool) {
            for (ElfFile.Symbol s : primaryTable(m.elf)) {
                if (!s.isUndefined() && !s.isLocal() && s.name.equals(r.sym.name)) {
                    Cand c = new Cand(new Def(m, s, null, false));
                    c.eliminate("archive 成员未抽取");
                    cands.add(c);
                }
            }
        }
        List<Cand> active = new ArrayList<>();
        for (Cand c : cands) {
            if (c.reason == null) {
                active.add(c);
            }
        }
        if (active.isEmpty()) {
            String status;
            if (sawVersionMismatch) {
                status = "UNDEFINED_VERSION";
            } else if (r.sym.isWeak()) {
                status = "WEAK_UNRESOLVED";
            } else {
                status = "UNDEFINED";
            }
            step("RESOLVE", r.obj.name + ": " + label(r) + " -> " + status);
            return new Verdict(r.obj.name, r.sym.name, r.version, status, null, 0, views(cands));
        }
        List<Cand> nonCommon = new ArrayList<>();
        for (Cand c : active) {
            if (!c.d.sym.isCommon()) {
                nonCommon.add(c);
            }
        }
        if (!nonCommon.isEmpty()) {
            for (Cand c : active) {
                if (c.d.sym.isCommon()) {
                    c.eliminate("common 被已定义符号覆盖");
                }
            }
            active = nonCommon;
        } else {
            long max = 0;
            for (Cand c : active) {
                max = Math.max(max, c.d.sym.size);
            }
            for (Cand c : active) {
                if (c.d.sym.size < max) {
                    c.eliminate("common 尺寸较小");
                }
            }
            active.removeIf(c -> c.reason != null);
        }
        List<Cand> strong = new ArrayList<>();
        for (Cand c : active) {
            if (!c.d.sym.isWeak()) {
                strong.add(c);
            }
        }
        if (!strong.isEmpty()) {
            for (Cand c : active) {
                if (c.d.sym.isWeak()) {
                    c.eliminate("weak 被 strong 定义覆盖");
                }
            }
            active = strong;
        }
        for (Cand c : active) {
            if (c.d.obj == r.obj && c.d.sym.visibility() == ElfFile.STV_PROTECTED) {
                for (Cand other : active) {
                    if (other != c) {
                        other.eliminate("protected 定义不可抢占");
                    }
                }
                active = List.of(c);
                break;
            }
        }
        if (policy.useLoadOrder() && active.size() > 1) {
            int bestOrder = active.stream().mapToInt(c -> c.d.obj.order).min().orElse(0);
            int bestSub = active.stream().filter(c -> c.d.obj.order == bestOrder)
                    .mapToInt(c -> c.d.obj.sub).min().orElse(0);
            for (Cand c : active) {
                if (c.d.obj.order != bestOrder || c.d.obj.sub != bestSub) {
                    c.eliminate("加载顺序靠后");
                }
            }
            active.removeIf(c -> c.reason != null);
        }
        String status;
        String chosenName = null;
        long address = 0;
        if (active.size() > 1) {
            status = "AMBIGUOUS";
            for (Cand c : active) {
                c.ambiguous = true;
            }
            step("RESOLVE", r.obj.name + ": " + label(r) + " -> AMBIGUOUS (" + active.size() + " 个同等候选)");
        } else {
            Cand win = active.get(0);
            win.chosen = true;
            chosenName = win.d.obj.name;
            if (win.d.sym.isCommon()) {
                status = "COMMON_ALLOCATED";
                address = commonAlloc;
                commonAlloc += Math.max(8, win.d.sym.size);
                step("RESOLVE", r.obj.name + ": " + label(r) + " -> common 分配于 " + chosenName);
            } else if (policy.copyRelocation()
                    && r.obj.elf.elfType() == ElfFile.ET_EXEC
                    && win.d.obj.elf.elfType() == ElfFile.ET_DYN
                    && win.d.sym.type() == 1) {
                status = "COPY_RELOC";
                address = copyAlloc;
                copyAlloc += Math.max(8, win.d.sym.size);
                step("RESOLVE", r.obj.name + ": " + label(r) + " -> copy relocation（不被普通覆盖吞掉）");
            } else {
                status = "RESOLVED";
                address = win.d.obj.base() + win.d.sym.value;
                step("RESOLVE", r.obj.name + ": " + label(r) + " -> " + chosenName);
            }
        }
        return new Verdict(r.obj.name, r.sym.name, r.version, status, chosenName, address, views(cands));
    }

    private static String label(Ref r) {
        return r.version == null ? r.sym.name : r.sym.name + "@" + r.version;
    }

    private boolean hiddenOutside(Def d, Ref r) {
        int vis = d.sym.visibility();
        return d.obj != r.obj
                && d.obj.elf.elfType() != ElfFile.ET_REL
                && (vis == ElfFile.STV_HIDDEN || vis == 1);
    }

    private static final class Cand {
        final Def d;
        String reason;
        boolean chosen;
        boolean ambiguous;

        Cand(Def d) {
            this.d = d;
        }

        void eliminate(String why) {
            if (reason == null) {
                reason = why;
            }
        }
    }

    private List<CandidateView> views(List<Cand> cands) {
        List<CandidateView> out = new ArrayList<>();
        for (Cand c : cands) {
            out.add(new CandidateView(
                    c.d.obj.name,
                    bindName(c.d.sym),
                    c.d.version,
                    c.d.defaultVersion,
                    c.d.sym.isCommon(),
                    c.chosen,
                    c.reason != null,
                    c.ambiguous,
                    c.reason == null ? (c.chosen ? "选中" : "同等候选") : c.reason));
        }
        return out;
    }

    private static String bindName(ElfFile.Symbol s) {
        String b = switch (s.bind()) {
            case ElfFile.STB_LOCAL -> "LOCAL";
            case ElfFile.STB_WEAK -> "WEAK";
            default -> "GLOBAL";
        };
        if (s.visibility() == ElfFile.STV_HIDDEN) {
            b += "/HIDDEN";
        } else if (s.visibility() == ElfFile.STV_PROTECTED) {
            b += "/PROTECTED";
        }
        return b;
    }

    private List<RelocView> buildRelocViews(Map<String, Verdict> byKey) {
        List<RelocView> out = new ArrayList<>();
        for (Obj o : objs) {
            List<ElfFile.Symbol> table = primaryTable(o.elf);
            boolean dyn = !o.elf.dynsym().isEmpty();
            for (ElfFile.Reloc rel : o.elf.relocs()) {
                if (rel.symIndex <= 0 || rel.symIndex >= table.size()) {
                    out.add(new RelocView(o.name, rel.sectionName, rel.offset, rel.type,
                            "<bad-index-" + rel.symIndex + ">", null, 0, "BAD_INDEX"));
                    continue;
                }
                ElfFile.Symbol s = table.get(rel.symIndex);
                if (s.isUndefined()) {
                    String version = null;
                    if (dyn) {
                        ElfFile.VersionRef vr = o.elf.dynsymVersion(s.index);
                        if (vr != null) {
                            version = vr.name;
                        }
                    }
                    Verdict v = byKey.get(o.name + "" + s.name + "" + (version == null ? "" : version));
                    if (v != null) {
                        out.add(new RelocView(o.name, rel.sectionName, rel.offset, rel.type,
                                s.name, v.chosen(), v.address(), v.status()));
                    } else {
                        out.add(new RelocView(o.name, rel.sectionName, rel.offset, rel.type,
                                s.name, null, 0, "LOCAL_UNDEF"));
                    }
                } else if (o.discardedSections.contains(s.shndx)) {
                    out.add(new RelocView(o.name, rel.sectionName, rel.offset, rel.type,
                            s.name, null, 0, "COMDAT_DISCARDED"));
                } else {
                    out.add(new RelocView(o.name, rel.sectionName, rel.offset, rel.type,
                            s.name, o.name, o.base() + s.value, "LOCAL"));
                }
            }
        }
        return out;
    }

    private List<ObjectView> objectViews(List<Verdict> verdicts) {
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (Obj o : objs) {
            deps.put(o.name, new TreeSet<>());
        }
        for (Verdict v : verdicts) {
            if (v.chosen() != null && !v.chosen().equals(v.object())) {
                deps.computeIfAbsent(v.object(), k -> new TreeSet<>()).add(v.chosen());
            }
        }
        List<ObjectView> out = new ArrayList<>();
        for (Obj o : objs) {
            String kind = switch (o.elf.elfType()) {
                case ElfFile.ET_REL -> "ET_REL";
                case ElfFile.ET_EXEC -> "ET_EXEC";
                case ElfFile.ET_DYN -> "ET_DYN";
                default -> "ET_NONE";
            };
            out.add(new ObjectView(o.name, kind, o.order, o.base(), o.sub > 0,
                    List.copyOf(deps.getOrDefault(o.name, Set.of()))));
        }
        return out;
    }

    private List<VersionNode> versionGraph() {
        List<VersionNode> out = new ArrayList<>();
        for (Obj o : objs) {
            for (ElfFile.VersionDef d : o.elf.versionDefs()) {
                out.add(new VersionNode(o.name, "defines", d.name, null));
            }
            for (ElfFile.VersionNeedEntry n : o.elf.versionNeeds()) {
                out.add(new VersionNode(o.name, "needs", n.name, n.file));
            }
        }
        return out;
    }

    /** Semantic diff: provider swaps vs pure address movement. */
    public static List<CompareEntry> compare(Result a, Result b) {
        Map<String, Verdict> mb = new LinkedHashMap<>();
        for (Verdict v : b.verdicts()) {
            mb.put(v.object() + "" + v.symbol() + "" + (v.version() == null ? "" : v.version()), v);
        }
        List<CompareEntry> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Verdict va : a.verdicts()) {
            String key = va.object() + "" + va.symbol() + "" + (va.version() == null ? "" : va.version());
            Verdict vb = mb.get(key);
            if (vb == null) {
                out.add(new CompareEntry(va.object(), va.symbol(), va.version(), "REMOVED",
                        va.chosen(), null, va.address(), 0));
                continue;
            }
            seen.add(key);
            String ca = va.chosen() == null ? "" : va.chosen();
            String cb = vb.chosen() == null ? "" : vb.chosen();
            if (!ca.equals(cb)) {
                out.add(new CompareEntry(va.object(), va.symbol(), va.version(), "PROVIDER_CHANGED",
                        va.chosen(), vb.chosen(), va.address(), vb.address()));
            } else if (va.address() != vb.address()) {
                out.add(new CompareEntry(va.object(), va.symbol(), va.version(), "ADDRESS_ONLY",
                        va.chosen(), vb.chosen(), va.address(), vb.address()));
            }
        }
        for (Verdict vb : b.verdicts()) {
            String key = vb.object() + "" + vb.symbol() + "" + (vb.version() == null ? "" : vb.version());
            if (!seen.contains(key) && a.verdicts().stream().noneMatch(va ->
                    (va.object() + "" + va.symbol() + "" + (va.version() == null ? "" : va.version())).equals(key))) {
                out.add(new CompareEntry(vb.object(), vb.symbol(), vb.version(), "ADDED",
                        null, vb.chosen(), 0, vb.address()));
            }
        }
        return out;
    }
}
