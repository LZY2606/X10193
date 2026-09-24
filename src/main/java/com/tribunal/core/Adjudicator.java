package com.tribunal.core;

import com.tribunal.elf.Archive;
import com.tribunal.elf.Elf;
import com.tribunal.elf.ElfException;
import com.tribunal.elf.ElfFile;
import com.tribunal.elf.ElfParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 符号裁决庭核心：不调用系统 linker，按显式加载顺序完成
 * strong/weak/common/local/visibility/版本/COMDAT/copy reloc/archive 惰性抽取的裁决。
 * 全程确定性：仅以用户给定加载顺序打破平局；同等候选保留歧义。
 */
public class Adjudicator {

    public static class InputObject {
        public final String name;
        public final byte[] data;

        public InputObject(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private static final int RANK_NONE = 0;
    private static final int RANK_COMMON = 1;
    private static final int RANK_WEAK = 2;
    private static final int RANK_STRONG = 3;

    /** 全局符号表条目。 */
    private static final class Entry {
        final String key;
        LoadedObject provider;
        ElfFile.Symbol sym;
        int rank = RANK_NONE;
        boolean ambiguous;
        boolean conflict;
        long commonSize;
        final List<Candidate> candidates = new ArrayList<>();

        Entry(String key) { this.key = key; }
    }

    /** 一处未定义引用。 */
    private static final class Ref {
        String key;
        String name;
        String version;         // 需求版本（verneed 还原）
        LoadedObject from;
        ElfFile.Symbol sym;
    }

    private final Policy policy;
    private final ElfParser parser = new ElfParser();
    private AdjudicationResult result;
    private final Map<String, Entry> global = new LinkedHashMap<>();
    private final List<Ref> refs = new ArrayList<>();
    private final Map<String, String> comdatOwners = new LinkedHashMap<>(); // signature -> object
    private int eventSeq;
    private int objectSeq;

    public Adjudicator(Policy policy) {
        this.policy = policy;
    }

    public AdjudicationResult adjudicate(List<InputObject> inputs) {
        result = new AdjudicationResult();
        global.clear();
        refs.clear();
        comdatOwners.clear();
        copyRelocKeys.clear();
        verdictByKey.clear();
        eventSeq = 0;
        objectSeq = 0;

        List<Archive> archives = new ArrayList<>();
        Map<Archive, Set<Integer>> extracted = new LinkedHashMap<>();

        // 1. 解析输入：归档保持惰性，普通对象立即解析；损坏对象只隔离自身。
        for (InputObject in : inputs) {
            if (Archive.isArchive(in.data)) {
                try {
                    Archive a = Archive.parse(in.data, in.name);
                    archives.add(a);
                    extracted.put(a, new LinkedHashSet<>());
                    event(ResolutionEvent.Kind.LOAD_OBJECT, "归档待命（惰性）: " + in.name
                            + " (" + a.members.size() + " 个成员)");
                } catch (ElfException e) {
                    isolate(in.name, e.getMessage());
                }
                continue;
            }
            ElfFile elf = tryParse(in.data, in.name);
            if (elf != null) loadObject(elf, in.name, false, null);
        }

        // 2. archive 惰性抽取直到不动点（覆盖循环依赖）。
        archiveFixpoint(archives, extracted);

        // 3. copy relocation（可执行文件引用共享库数据对象）。
        applyCopyRelocations();

        // 4. 生成裁决与重定位归属。
        buildVerdicts();
        attachRelocations();
        buildDependencies();
        return result;
    }

    private ElfFile tryParse(byte[] data, String name) {
        try {
            return parser.parse(data, name);
        } catch (ElfException | RuntimeException e) {
            isolate(name, e.getMessage());
            return null;
        }
    }

    private void isolate(String name, String reason) {
        AdjudicationResult.CorruptObject c = new AdjudicationResult.CorruptObject();
        c.name = name;
        c.reason = reason;
        result.corrupt.add(c);
        event(ResolutionEvent.Kind.ISOLATE_CORRUPT, "隔离损坏对象 " + name + ": " + reason);
    }

    private void event(ResolutionEvent.Kind kind, String message) {
        result.events.add(new ResolutionEvent(eventSeq++, kind, message));
    }

    private LoadedObject loadObject(ElfFile elf, String name, boolean fromArchive, String archiveName) {
        LoadedObject o = new LoadedObject();
        o.seq = objectSeq++;
        o.name = name;
        o.elf = elf;
        o.fromArchive = fromArchive;
        o.archiveName = archiveName;
        applyComdat(o);
        result.objects.add(o);
        event(ResolutionEvent.Kind.LOAD_OBJECT,
                (fromArchive ? "载入（archive 成员）" : "载入") + " " + name
                        + " [" + elfTypeName(elf) + "]");
        registerSymbols(o);
        collectRefs(o);
        return o;
    }

    private static String elfTypeName(ElfFile f) {
        return switch (f.type) {
            case Elf.ET_REL -> "ET_REL";
            case Elf.ET_EXEC -> "ET_EXEC";
            case Elf.ET_DYN -> "ET_DYN";
            default -> "ET#" + f.type;
        };
    }

    /** COMDAT section group 去重：签名先到先留，不被普通覆盖规则吞掉。 */
    private void applyComdat(LoadedObject o) {
        for (ElfFile.Group g : o.elf.groups) {
            if (!g.comdat) continue;
            String owner = comdatOwners.get(g.signature);
            if (owner != null) {
                o.droppedSections.addAll(g.sectionIndices);
                event(ResolutionEvent.Kind.COMDAT_DEDUP, "COMDAT 去重: " + o.name
                        + " 的组 \"" + g.signature + "\" 被丢弃（保留 " + owner + " 的副本）");
            } else {
                comdatOwners.put(g.signature, o.name);
                event(ResolutionEvent.Kind.COMDAT_DEDUP, "COMDAT 保留: " + o.name
                        + " 的组 \"" + g.signature + "\" (" + g.sectionIndices.size() + " 个 section)");
            }
        }
    }

    private List<ElfFile.Symbol> exportedSymbols(LoadedObject o) {
        // 共享库以 .dynsym 为对外接口；可重定位/可执行对象用 .symtab（缺失时退回 .dynsym）。
        if (o.elf.isShared()) return o.elf.dynSymbols;
        return o.elf.symbols.isEmpty() ? o.elf.dynSymbols : o.elf.symbols;
    }

    private void registerSymbols(LoadedObject o) {
        for (ElfFile.Symbol s : exportedSymbols(o)) {
            if (s.isUndefined() || s.isLocal()) continue;
            if (s.name == null || s.name.isEmpty()) continue;
            if (s.shndx > 0 && s.shndx < 0xff00 && o.sectionDropped(s.shndx)) continue; // COMDAT 已丢弃
            if (o.elf.isShared() && (s.visibility == Elf.STV_HIDDEN || s.visibility == Elf.STV_INTERNAL)) {
                continue; // hidden/internal 不进入全局动态作用域
            }
            int rank = rankOf(s);
            if (rank == RANK_NONE) continue;

            Set<String> keys = new LinkedHashSet<>();
            if (s.versionName != null && s.dynamic) {
                keys.add(s.name + "@" + s.versionName);
                if (!s.versionHidden) keys.add(s.name); // 默认版本同时满足未版本化引用
            } else {
                keys.add(s.name);
            }
            for (String key : keys) {
                mergeDef(key, o, s, rank);
            }
        }
    }

    private int rankOf(ElfFile.Symbol s) {
        if (s.isCommon()) return RANK_COMMON;
        if (s.bind == Elf.STB_WEAK) return RANK_WEAK;
        if (s.bind == Elf.STB_GLOBAL || s.bind == Elf.STB_GNU_UNIQUE) return RANK_STRONG;
        return RANK_NONE;
    }

    private void mergeDef(String key, LoadedObject o, ElfFile.Symbol s, int rank) {
        Entry e = global.computeIfAbsent(key, Entry::new);
        Candidate cand = Candidate.of(o.name, s);
        if (e.rank == RANK_NONE || rank > e.rank) {
            if (e.rank != RANK_NONE) {
                for (Candidate old : e.candidates) {
                    if (!old.eliminated) {
                        old.eliminated = true;
                        old.reason = "被更强的定义覆盖（" + rankName(e.rank) + " → " + rankName(rank) + "）";
                    }
                }
            }
            cand.eliminated = false;
            cand.reason = "选中（" + rankName(rank) + "）";
            e.candidates.add(cand);
            e.provider = o;
            e.sym = s;
            e.rank = rank;
            if (rank == RANK_COMMON) e.commonSize = s.size;
            event(ResolutionEvent.Kind.SYMBOL_UPDATE, "符号表更新: " + key + " → " + o.name
                    + " (" + rankName(rank) + ")");
            return;
        }
        if (rank == RANK_COMMON && e.rank == RANK_COMMON) {
            // common 合并：尺寸取最大，不判歧义
            e.commonSize = Math.max(e.commonSize, s.size);
            cand.eliminated = false;
            cand.reason = "common 合并（尺寸取 max=" + e.commonSize + "）";
            e.candidates.add(cand);
            event(ResolutionEvent.Kind.SYMBOL_UPDATE, "common 合并: " + key + " 尺寸=" + e.commonSize);
            return;
        }
        if (rank == RANK_STRONG && e.rank == RANK_STRONG) {
            e.conflict = true;
            cand.eliminated = !policy.tolerateMultipleStrongDefs;
            cand.reason = policy.tolerateMultipleStrongDefs
                    ? "重复 strong 定义，按加载顺序保留首个"
                    : "重复 strong 定义（冲突）";
            e.candidates.add(cand);
            event(ResolutionEvent.Kind.SYMBOL_UPDATE, "冲突: " + key + " 在 " + o.name
                    + " 与 " + e.provider.name + " 均为 strong 定义");
            return;
        }
        if (rank == RANK_WEAK && e.rank == RANK_WEAK) {
            // 同等候选：保留歧义，确定性取加载顺序首个
            e.ambiguous = true;
            cand.eliminated = true;
            cand.reason = "同等 weak 候选，歧义保留（加载顺序首个胜出）";
            e.candidates.add(cand);
            event(ResolutionEvent.Kind.SYMBOL_UPDATE, "歧义: " + key + " 存在多个同等 weak 定义（"
                    + e.provider.name + ", " + o.name + "）");
            return;
        }
        // 更弱的定义被现有条目覆盖
        cand.eliminated = true;
        cand.reason = "被已有更强定义覆盖（" + rankName(e.rank) + "）";
        e.candidates.add(cand);
    }

    private static String rankName(int rank) {
        return switch (rank) {
            case RANK_COMMON -> "common";
            case RANK_WEAK -> "weak";
            case RANK_STRONG -> "strong";
            default -> "undef";
        };
    }

    private void collectRefs(LoadedObject o) {
        for (ElfFile.Symbol s : exportedSymbols(o)) {
            if (!s.isUndefined() || s.isLocal()) continue;
            if (s.name == null || s.name.isEmpty()) continue;
            Ref r = new Ref();
            r.name = s.name;
            r.version = (s.dynamic && s.versionName != null) ? s.versionName : null;
            r.key = r.version != null ? s.name + "@" + s.version : s.name;
            r.from = o;
            r.sym = s;
            refs.add(r);
        }
    }

    /** archive 惰性抽取：未定义引用驱动，直到不动点（覆盖成员间/归档间循环依赖）。 */
    private void archiveFixpoint(List<Archive> archives, Map<Archive, Set<Integer>> extracted) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> needed = unsatisfiedKeys();
            if (needed.isEmpty()) break;
            for (Archive a : archives) {
                Set<Integer> done = extracted.get(a);
                for (int i = 0; i < a.members.size(); i++) {
                    if (done.contains(i)) continue;
                    Archive.Member m = a.members.get(i);
                    ElfFile elf;
                    try {
                        elf = parser.parse(m.data, a.name + "(" + m.name + ")");
                    } catch (ElfException | RuntimeException e) {
                        done.add(i);
                        isolate(a.name + "(" + m.name + ")", e.getMessage());
                        continue;
                    }
                    Set<String> defs = definedKeys(elf);
                    String trigger = null;
                    for (String k : needed) {
                        if (defs.contains(k)) { trigger = k; break; }
                    }
                    if (trigger != null) {
                        done.add(i);
                        event(ResolutionEvent.Kind.ARCHIVE_EXTRACT, "抽取 " + a.name + "(" + m.name
                                + ")：提供未定义符号 " + trigger);
                        loadObject(elf, a.name + "(" + m.name + ")", true, a.name);
                        changed = true;
                    }
                }
            }
        }
    }

    /** 当前未被满足的键（弱符号的未定义引用不触发抽取）。 */
    private Set<String> unsatisfiedKeys() {
        Set<String> needed = new LinkedHashSet<>();
        for (Ref r : refs) {
            if (r.sym.isWeak()) continue;
            Entry e = global.get(r.key);
            boolean satisfied = e != null && e.rank > RANK_NONE;
            if (!satisfied && r.version == null) {
                // 未版本化引用也可被该对象的默认版本满足
                satisfied = hasAnyDefFor(r.name);
            }
            if (!satisfied) needed.add(r.key);
        }
        return needed;
    }

    private boolean hasAnyDefFor(String name) {
        Entry plain = global.get(name);
        if (plain != null && plain.rank > RANK_NONE) return true;
        for (Map.Entry<String, Entry> en : global.entrySet()) {
            if (en.getKey().startsWith(name + "@") && en.getValue().rank > RANK_NONE) return true;
        }
        return false;
    }

    /** 成员若被抽取，会提供哪些键。 */
    private Set<String> definedKeys(ElfFile elf) {
        Set<String> keys = new LinkedHashSet<>();
        List<ElfFile.Symbol> syms = elf.isShared() ? elf.dynSymbols
                : (elf.symbols.isEmpty() ? elf.dynSymbols : elf.symbols);
        for (ElfFile.Symbol s : syms) {
            if (s.isUndefined() || s.isLocal() || s.name == null || s.name.isEmpty()) continue;
            if (elf.isShared() && (s.visibility == Elf.STV_HIDDEN || s.visibility == Elf.STV_INTERNAL)) continue;
            if (rankOf(s) == RANK_NONE) continue;
            if (s.versionName != null && s.dynamic) {
                keys.add(s.name + "@" + s.versionName);
                if (!s.versionHidden) keys.add(s.name);
            } else {
                keys.add(s.name);
            }
        }
        return keys;
    }

    /** copy relocation：不能被普通覆盖规则吞掉，protected 不可复制。 */
    private void applyCopyRelocations() {
        for (Ref r : refs) {
            if (!r.from.elf.isExecutable() || !policy.copyRelocations) continue;
            if (r.sym.type != Elf.STT_OBJECT) continue;
            Entry e = global.get(r.key);
            if (e == null || e.rank == RANK_NONE || e.sym == null) continue;
            if (!e.provider.elf.isShared()) continue;
            if (e.sym.visibility == Elf.STV_PROTECTED) continue; // protected 禁止 copy reloc
            copyRelocKeys.add(r.key);
            event(ResolutionEvent.Kind.COPY_RELOC, "copy relocation: " + r.key + " 从 "
                    + e.provider.name + " 复制到 " + r.from.name + " 的 .bss（尺寸 "
                    + Math.max(e.sym.size, r.sym.size) + "）");
        }
    }

    private final Set<String> copyRelocKeys = new LinkedHashSet<>();

    private void buildVerdicts() {
        Map<String, Verdict> byKey = new LinkedHashMap<>();
        // 先为有定义的键建结论
        for (Map.Entry<String, Entry> en : global.entrySet()) {
            Entry e = en.getValue();
            if (e.rank == RANK_NONE) continue;
            Verdict v = new Verdict();
            int at = e.key.indexOf('@');
            v.symbolName = at < 0 ? e.key : e.key.substring(0, at);
            v.version = at < 0 ? null : e.key.substring(at + 1);
            v.candidates.addAll(e.candidates);
            if (e.conflict && !policy.tolerateMultipleStrongDefs) {
                v.status = Verdict.Status.CONFLICT;
                v.chosen = null;
                v.reason = "多个 strong 定义冲突，无唯一胜者";
            } else if (e.ambiguous) {
                v.status = Verdict.Status.AMBIGUOUS;
                v.chosen = e.provider.name;
                v.reason = "多个同等候选，歧义保留；按加载顺序暂取 " + e.provider.name;
            } else {
                v.status = Verdict.Status.RESOLVED;
                v.chosen = e.provider.name;
                v.reason = "唯一胜出者（" + rankName(e.rank) + "）";
            }
            if (copyRelocKeys.contains(e.key)) {
                v.copyReloc = true;
                v.reason += "；经 copy relocation 绑定到可执行文件本地副本";
            }
            byKey.put(e.key, v);
            result.verdicts.add(v);
            event(ResolutionEvent.Kind.VERDICT, "裁决 " + e.key + " → "
                    + (v.chosen == null ? v.status : v.chosen));
        }
        // 再处理未被满足的引用
        Set<String> seen = new LinkedHashSet<>();
        for (Ref r : refs) {
            Entry e = global.get(r.key);
            if (e != null && e.rank > RANK_NONE) {
                // 版本化引用需校验提供者版本
                if (r.version != null && e.sym != null
                        && !r.version.equals(e.sym.versionName)) {
                    String k = r.key + "!" + r.from.name;
                    if (seen.add(k)) {
                        Verdict v = unsatisfied(r, "提供者版本为 "
                                + (e.sym.versionName == null ? "（无版本）" : e.sym.versionName));
                        collectSameNameCandidates(v, r.name);
                        result.verdicts.add(v);
                        event(ResolutionEvent.Kind.VERDICT, "版本未满足: " + r.key
                                + "（" + r.from.name + " 引用）");
                    }
                }
                continue;
            }
            String k = r.key + "!" + r.from.name;
            if (!seen.add(k)) continue;
            if (r.sym.isWeak() && policy.undefinedWeakResolvesToZero) {
                Verdict v = new Verdict();
                v.symbolName = r.name;
                v.version = r.version;
                v.status = Verdict.Status.WEAK_UNDEFINED;
                v.chosen = null;
                v.reason = "未定义弱符号，按策略解析为 0";
                v.candidates.add(refCandidate(r));
                result.verdicts.add(v);
                event(ResolutionEvent.Kind.VERDICT, "弱未定义: " + r.key + " → 0");
            } else if (r.version != null) {
                Verdict v = unsatisfied(r, "无任何对象定义版本 " + r.version);
                collectSameNameCandidates(v, r.name);
                result.verdicts.add(v);
                event(ResolutionEvent.Kind.VERDICT, "版本未满足: " + r.key
                        + "（" + r.from.name + " 引用）");
            } else {
                Verdict v = new Verdict();
                v.symbolName = r.name;
                v.status = Verdict.Status.UNRESOLVED;
                v.chosen = null;
                v.reason = "无任何候选提供者";
                v.candidates.add(refCandidate(r));
                result.verdicts.add(v);
                event(ResolutionEvent.Kind.VERDICT, "未解析: " + r.key
                        + "（" + r.from.name + " 引用）");
            }
        }
        verdictByKey.clear();
        for (Verdict v : result.verdicts) {
            verdictByKey.putIfAbsent(v.key(), v);
        }
    }

    private Verdict unsatisfied(Ref r, String detail) {
        Verdict v = new Verdict();
        v.symbolName = r.name;
        v.version = r.version;
        v.status = Verdict.Status.UNSATISFIED_VERSION;
        v.chosen = null;
        v.reason = "版本需求未满足: " + detail;
        v.candidates.add(refCandidate(r));
        return v;
    }

    private Candidate refCandidate(Ref r) {
        Candidate c = Candidate.of(r.from.name + "（引用）", r.sym);
        c.eliminated = true;
        c.reason = "未满足引用方";
        return c;
    }

    /** 版本未满足时，列出同名但版本不符的候选。 */
    private void collectSameNameCandidates(Verdict v, String name) {
        for (Map.Entry<String, Entry> en : global.entrySet()) {
            String k = en.getKey();
            if (!k.equals(name) && !k.startsWith(name + "@")) continue;
            Entry e = en.getValue();
            if (e.rank == RANK_NONE || e.sym == null) continue;
            Candidate c = Candidate.of(e.provider.name, e.sym);
            c.eliminated = true;
            c.reason = "版本不符（需要 " + (v.version == null ? "?" : v.version) + "）";
            v.candidates.add(c);
        }
    }

    private final Map<String, Verdict> verdictByKey = new LinkedHashMap<>();

    /** 把每条重定位挂到对应符号的裁决上。 */
    private void attachRelocations() {
        for (LoadedObject o : result.objects) {
            List<ElfFile.Symbol> syms = o.elf.symbols;
            List<ElfFile.Symbol> dsyms = o.elf.dynSymbols;
            for (ElfFile.Reloc rel : o.elf.relocs) {
                List<ElfFile.Symbol> table = rel.dynamic ? dsyms : syms;
                if (rel.symIndex >= table.size()) continue;
                ElfFile.Symbol s = table.get(rel.symIndex);
                RelocRef rr = new RelocRef();
                rr.object = o.name;
                rr.section = rel.sectionName;
                rr.offset = rel.offset;
                rr.type = rel.relocType;
                if (s.isLocal() || (s.visibility == Elf.STV_HIDDEN && !s.isUndefined())) {
                    rr.provider = o.name + "（本地）";
                } else {
                    String key = (s.dynamic && s.versionName != null && s.isUndefined())
                            ? s.name + "@" + s.versionName : s.name;
                    Verdict v = verdictByKey.get(key);
                    if (v == null && s.versionName == null) v = verdictByKey.get(s.name);
                    rr.provider = v != null && v.chosen != null ? v.chosen : "（未解析）";
                    if (v != null) {
                        v.relocations.add(rr);
                        continue;
                    }
                }
                // 本地符号重定位不进入全局裁决列表
            }
        }
    }

    private void buildDependencies() {
        Set<String> edges = new LinkedHashSet<>();
        for (Ref r : refs) {
            Entry e = global.get(r.key);
            if (e == null || e.rank == RANK_NONE || e.provider == null) continue;
            if (e.provider == r.from) continue;
            addEdge(r.from.name, e.provider.name, "PROVIDES", edges);
        }
        for (LoadedObject o : result.objects) {
            for (String need : o.elf.needed) {
                String provider = null;
                for (LoadedObject other : result.objects) {
                    if (other == o) continue;
                    if (need.equals(other.elf.soname) || need.equals(other.name)
                            || (other.elf.soname != null && other.elf.soname.equals(need))) {
                        provider = other.name;
                        break;
                    }
                }
                addEdge(o.name, provider != null ? provider : need + "（未加载）", "NEEDED", edges);
            }
        }
    }

    private void addEdge(String from, String to, String kind, Set<String> edges) {
        String id = from + "→" + to + ":" + kind;
        if (edges.add(id)) {
            AdjudicationResult.Dependency d = new AdjudicationResult.Dependency();
            d.from = from;
            d.to = to;
            d.kind = kind;
            result.dependencies.add(d);
        }
    }
}
