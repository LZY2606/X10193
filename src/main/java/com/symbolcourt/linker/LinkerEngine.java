package com.symbolcourt.linker;

import com.symbolcourt.ar.ArException;
import com.symbolcourt.ar.ArMember;
import com.symbolcourt.ar.ArReader;
import com.symbolcourt.elf.Elf;
import com.symbolcourt.elf.ElfException;
import com.symbolcourt.elf.ElfParser;
import com.symbolcourt.model.ElfFile;
import com.symbolcourt.model.Reloc;
import com.symbolcourt.model.Section;
import com.symbolcourt.model.Sym;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 纯 Java 符号裁决引擎：不调用系统 linker。
 * 输出每个引用的候选、淘汰原因、最终选择与受影响重定位，并产生可播放事件流。
 */
public class LinkerEngine {

    private final ResolutionResult result = new ResolutionResult();
    private final List<Event> events = new ArrayList<>();
    private int step;
    private final Map<String, Candidate> gst = new LinkedHashMap<>();

    public ResolutionResult resolve(List<LoadedObject> inputs, Policy policy) {
        result.policy = policy;
        result.generatedAt = System.currentTimeMillis();

        loadInputs(inputs);
        selectComdatGroups();
        playSequence();
        collectAndDecide();
        assignSimulatedAddresses();
        result.references.forEach(this::finalizeEventsForReference);
        finishEvents();
        for (String u : result.unresolvedStrong()) {
            result.errors.add("未定义的强引用: " + u);
        }
        return result;
    }

    /** 由 service 预解析好的输入（坏对象仅标记隔离）。 */
    public static class LoadedObject {
        public String name;
        public byte[] data;
        public ElfFile elf;
        public boolean archive;
        public List<ArMember> members = new ArrayList<>();
        public String parseError;

        public static LoadedObject parse(String name, byte[] data) {
            LoadedObject o = new LoadedObject();
            o.name = name;
            o.data = data;
            if (ArReader.isArchive(data)) {
                o.archive = true;
                try {
                    o.members = new ArReader().read(data);
                } catch (ArException e) {
                    o.parseError = "archive 损坏: " + e.getMessage();
                }
            } else {
                try {
                    o.elf = new ElfParser().parse(data, name);
                } catch (ElfException e) {
                    o.parseError = "ELF 解析失败: " + e.getMessage();
                }
            }
            return o;
        }
    }

    private void event(String kind, String title, String detail) {
        Event e = new Event(step++, kind, title, detail);
        for (Map.Entry<String, Candidate> en : gst.entrySet()) {
            Candidate c = en.getValue();
            String[] parts = en.getKey().split("\0", 2);
            e.snapshot.add(new SymbolStatus(parts[0], parts.length > 1 ? parts[1] : null,
                    c, c == null ? "EMPTY" : "PROVIDED", ""));
        }
        events.add(e);
        result.events = events;
    }

    // ------------------------------------------------------------------
    // 版本索引 -> 版本名
    // ------------------------------------------------------------------

    VersionInfo versionOf(ElfFile f, Sym s) {
        if (s.versym < 0) {
            return null;
        }
        int idx = s.versym & Elf.VERSYM_VERSION;
        boolean hidden = (s.versym & Elf.VERSYM_HIDDEN) != 0;
        if (idx == Elf.VER_NDX_LOCAL) {
            return new VersionInfo("*local*", false, false, true);
        }
        if (idx == Elf.VER_NDX_GLOBAL) {
            return null;
        }
        if (s.dynamic && !s.defined) {
            for (var need : f.versionNeeds) {
                if (need.index == idx) {
                    String n = need.names.isEmpty() ? "?" : need.names.get(0);
                    return new VersionInfo(n, false, false, false);
                }
            }
            return new VersionInfo("v" + idx, false, false, false);
        }
        for (var def : f.versionDefs) {
            if (def.index == idx) {
                return new VersionInfo(def.name, true, hidden, false);
            }
        }
        return new VersionInfo("v" + idx, true, hidden, false);
    }

    String gk(String name, String version) {
        return name + "\0" + (version == null ? "" : version);
    }

    private String groupSignature(ElfFile f, Section group) throws ElfException {
        if (group.link < 0 || group.link >= f.sections.size()) {
            throw new ElfException("SHT_GROUP sh_link 越界");
        }
        Section symSec = f.sections.get(group.link);
        if (symSec.type != Elf.SHT_SYMTAB && symSec.type != Elf.SHT_DYNSYM) {
            return "g" + group.index;
        }
        boolean dyn = symSec.type == Elf.SHT_DYNSYM;
        Sym sym = dyn ? f.dynamicSymbolAt(group.info) : f.symbolAt(group.info);
        return sym == null || sym.name.isEmpty() ? "g" + group.index : sym.name;
    }

    // ------------------------------------------------------------------
    // 载入
    // ------------------------------------------------------------------

    private void loadInputs(List<LoadedObject> inputs) {
        int order = 0;
        for (LoadedObject o : inputs) {
            LinkUnit u = new LinkUnit();
            u.order = order++;
            u.objectName = o.name;
            u.archive = o.archive;
            if (o.parseError != null) {
                u.kind = "ERROR";
                u.notNeededReason = o.parseError;
                result.warnings.add(o.name + " 已隔离: " + o.parseError);
                result.units.add(u);
                event("INPUT", "隔离损坏对象", o.name + " — " + o.parseError);
                continue;
            }
            if (o.archive) {
                u.kind = "AR";
                for (ArMember am : o.members) {
                    LinkUnit.Member m = new LinkUnit.Member();
                    m.name = am.name;
                    try {
                        m.elf = new ElfParser().parse(am.data, o.name + "(" + am.name + ")");
                        scanMember(m);
                        u.members.add(m);
                    } catch (ElfException e) {
                        result.warnings.add(o.name + "(" + am.name + ") 已隔离: " + e.getMessage());
                    }
                }
                event("INPUT", "载入 archive（惰性抽取）", o.name + "，候选成员 " + u.members.size() + " 个");
            } else {
                u.elf = o.elf;
                u.kind = o.elf.typeName();
                u.isExec = o.elf.type == Elf.ET_EXEC;
                event("INPUT", "载入 " + u.kind + " 对象", o.name);
            }
            result.units.add(u);
        }
    }

    private void scanMember(LinkUnit.Member m) {
        for (Sym s : m.elf.symbols) {
            if (s.dynamic || s.bind == Elf.STB_LOCAL || s.type == Elf.STT_SECTION) {
                continue;
            }
            if (s.defined && exportsStatic(m.elf, s)) {
                m.provides.add(s.name);
            } else if (!s.defined && s.shndx == Elf.SHN_UNDEF) {
                m.undefined.add(s.name);
            }
        }
    }

    /** 静态链接视角下，该定义是否对其他对象可见。 */
    private boolean exportsStatic(ElfFile f, Sym s) {
        if (s.bind == Elf.STB_LOCAL) {
            return false;
        }
        if (s.type == Elf.STT_SECTION) {
            return false;
        }
        if (f.type == Elf.ET_REL) {
            return s.visibility != Elf.STV_HIDDEN;
        }
        // 共享对象：动态表里的 HIDDEN 根本不出现；出现在 dynsym 的即导出
        return s.visibility != Elf.STV_HIDDEN;
    }

    // ------------------------------------------------------------------
    // COMDAT 去重：跨全部 relocatable（含 archive 成员）
    // ------------------------------------------------------------------

    private void selectComdatGroups() {
        // signature -> 胜出位置
        Map<String, String[]> winner = new LinkedHashMap<>();
        Map<String, List<String>> losers = new LinkedHashMap<>();
        for (LinkUnit u : result.units) {
            if (u.kind.equals("ERROR") || u.kind.equals("AR")) {
                if (u.archive) {
                    visitMemberComdats(u, winner, losers);
                }
                continue;
            }
            if (u.elf != null && u.elf.type == Elf.ET_REL) {
                visitElfComdats(u, null, u.elf, winner, losers);
            }
        }
        for (Map.Entry<String, String[]> en : winner.entrySet()) {
            ResolutionResult.ComdatGroup g = new ResolutionResult.ComdatGroup();
            g.name = en.getKey();
            String[] w = en.getValue();
            g.chosenObject = w[0];
            g.chosenMember = w[1];
            g.discarded.addAll(losers.getOrDefault(en.getKey(), List.of()));
            result.comdatGroups.add(g);
        }
        for (ResolutionResult.ComdatGroup g : result.comdatGroups) {
            event("COMDAT", "COMDAT 组去重: " + g.name,
                    "保留 " + (g.chosenMember != null ? g.chosenObject + "(" + g.chosenMember + ")"
                            : g.chosenObject)
                            + "；舍弃副本 " + g.discarded.size() + " 个");
        }
    }

    private void visitMemberComdats(LinkUnit u, Map<String, String[]> winner,
                                    Map<String, List<String>> losers) {
        for (LinkUnit.Member m : u.members) {
            visitElfComdats(u, m, m.elf, winner, losers);
        }
    }

    private void visitElfComdats(LinkUnit u, LinkUnit.Member m, ElfFile f,
                                 Map<String, String[]> winner, Map<String, List<String>> losers) {
        if (f == null) {
            return;
        }
        for (int gi : f.groupSections) {
            Section g = f.sections.get(gi);
            if ((g.info & Elf.GRP_COMDAT) == 0) {
                continue;
            }
            String sig;
            try {
                sig = groupSignature(f, g);
            } catch (ElfException e) {
                sig = "g" + gi + "@" + f.name;
            }
            String label = m != null ? u.objectName + "(" + m.name + ")" : u.objectName;
            boolean take = !winner.containsKey(sig);
            if (result.policy.comdatPick.equals("largest")) {
                String[] cur = winner.get(sig);
                long curSize = cur == null ? -1 : comdatSize(cur, sig);
                long newSize = comdatSize(new String[]{u.objectName, m == null ? null : m.name}, sig);
                take = newSize > curSize;
            }
            if (take) {
                String[] old = winner.put(sig, new String[]{u.objectName, m == null ? null : m.name});
                if (old != null) {
                    losers.computeIfAbsent(sig, k -> new ArrayList<>())
                            .add(old[1] != null ? old[0] + "(" + old[1] + ")" : old[0]);
                    discardComdat(old, sig);
                }
            } else {
                losers.computeIfAbsent(sig, k -> new ArrayList<>()).add(label);
                discardComdat(new String[]{u.objectName, m == null ? null : m.name}, sig);
            }
        }
    }

    private long comdatSize(String[] loc, String sig) {
        for (LinkUnit u : result.units) {
            if (!u.objectName.equals(loc[0])) {
                continue;
            }
            ElfFile f;
            if (loc[1] != null) {
                f = u.members.stream().filter(mm -> mm.name.equals(loc[1]))
                        .map(mm -> mm.elf).findFirst().orElse(null);
            } else {
                f = u.elf;
            }
            if (f == null) {
                return -1;
            }
            long total = 0;
            for (Section s : f.sections) {
                if (s.inGroup() && s.type != Elf.SHT_NOBITS) {
                    total += s.size;
                }
            }
            return total;
        }
        return -1;
    }

    private void discardComdat(String[] loc, String sig) {
        for (LinkUnit u : result.units) {
            if (!u.objectName.equals(loc[0])) {
                continue;
            }
            if (loc[1] == null) {
                u.discardedComdats.add(sig);
            } else {
                u.discardedMemberComdats.add(sig + "@" + loc[1]);
            }
        }
    }

    /** 该符号是否位于被 COMDAT 去重舍弃的节中。 */
    private boolean inDiscardedComdat(LinkUnit u, LinkUnit.Member m, ElfFile f, Sym s) {
        if (s.shndx < 0 || s.shndx >= f.sections.size()) {
            return false;
        }
        Section sec = f.sections.get(s.shndx);
        if (sec.groupIndex < 0) {
            return false;
        }
        String sig;
        try {
            sig = groupSignature(f, f.sections.get(sec.groupIndex));
        } catch (ElfException e) {
            return false;
        }
        if (m == null) {
            return u.discardedComdats.contains(sig);
        }
        return u.discardedMemberComdats.contains(sig + "@" + m.name);
    }

    // ------------------------------------------------------------------
    // 顺序播放
    // ------------------------------------------------------------------

    /** 当前未决的强/弱引用名（弱单独标记）。 */
    private final Set<String> pendingStrong = new LinkedHashSet<>();
    private final Set<String> pendingWeak = new LinkedHashSet<>();
    /** 首个可满足某名字的动态候选（位置之后可能再被静态定义覆盖）。 */
    private final Map<String, Candidate> firstDynamic = new LinkedHashMap<>();

    private void playSequence() {
        Policy p = result.policy;
        event("POLICY", "应用平台策略", p.fingerprint());

        for (LinkUnit u : result.units) {
            if ("ERROR".equals(u.kind)) {
                continue;
            }
            if (u.archive) {
                scanArchive(u);
                continue;
            }
            if (u.elf.type == Elf.ET_REL || u.elf.type == Elf.ET_EXEC) {
                includeStatic(u, null, u.elf);
            } else if (u.elf.type == Elf.ET_DYN) {
                processShared(u);
            }
        }

        if (p.repeatArchiveGroups) {
            boolean changed = true;
            int rounds = 0;
            while (changed && rounds++ < 64) {
                changed = false;
                for (LinkUnit u : result.units) {
                    if (u.archive && scanArchive(u)) {
                        changed = true;
                    }
                }
            }
            if (rounds > 1) {
                event("EXTRACT", "archive 组重复扫描定点", "共 " + (rounds - 1) + " 轮");
            }
        }
    }

    private void includeStatic(LinkUnit u, LinkUnit.Member m, ElfFile f) {
        if (m != null) {
            m.extracted = true;
            event("EXTRACT", "抽取 archive 成员",
                    u.objectName + "(" + m.name + ")" + (m.pulledBy == null ? "" : "，由 " + m.pulledBy + " 触发"));
        } else {
            u.included = true;
            event("INCLUDE", "纳入链接", u.objectName);
        }
        for (Sym s : f.symbols) {
            if (s.dynamic || s.bind == Elf.STB_LOCAL || !s.defined || s.type == Elf.STT_SECTION) {
                continue;
            }
            if (inDiscardedComdat(u, m, f, s)) {
                continue;
            }
            if (!exportsStatic(f, s)) {
                continue;
            }
            upsertStatic(u, m, f, s);
        }
        // 收集该对象新产生的未定义引用
        for (Sym s : f.symbols) {
            if (s.dynamic || s.bind == Elf.STB_LOCAL || s.defined || s.type == Elf.STT_SECTION) {
                continue;
            }
            if (s.bind == Elf.STB_WEAK) {
                pendingWeak.add(s.name);
            } else {
                pendingStrong.add(s.name);
                pendingWeak.remove(s.name);
            }
        }
        satisfyFromKnownDynamic();
    }

    private void upsertStatic(LinkUnit u, LinkUnit.Member m, ElfFile f, Sym s) {
        Candidate c = new Candidate();
        c.objectName = u.objectName;
        c.memberName = m == null ? null : m.name;
        c.provider = f.name;
        c.symbol = s;
        c.staticDef = true;
        c.bind = s.bind;
        c.visibility = s.visibility;
        c.common = s.common;
        VersionInfo vi = versionOf(f, s);
        c.versionName = vi == null || vi.special ? null : vi.name;
        c.versionDefault = vi == null || (!vi.nonDefault && vi.definedVersion);

        String key = gk(s.name, c.versionName);
        Candidate prev = gst.get(key);
        if (prev == null) {
            gst.put(key, c);
            pendingStrong.remove(s.name);
            pendingWeak.remove(s.name);
            firstDynamic.remove(s.name);
            event("SYMBOL_UPDATE", "符号表新增", label(c) + " 定义 " + s.name
                    + describe(c) + "（" + Elf.bindName(s.bind) + (s.common ? "/COMMON" : "") + "）");
            return;
        }
        Candidate win = mergeStatic(prev, c);
        gst.put(key, win);
        Candidate lost = win == prev ? c : prev;
        lost.reason = win == c ? Reason.ORDER_SHADOWED : lost.reason;
        event("SYMBOL_UPDATE", "符号表更新: " + s.name,
                label(win) + " 保留；" + label(lost) + " 出局 — " + describeClash(prev, c, win));
    }

    private Candidate mergeStatic(Candidate a, Candidate b) {
        boolean ac = a.common;
        boolean bc = b.common;
        if (ac && bc) {
            long aa = a.symbol.value;
            long bb = b.symbol.value;
            if (bb > aa) {
                b.reason = Reason.PROVIDED;
                a.reason = Reason.COMMON_SMALLER;
                return b;
            }
            a.reason = Reason.PROVIDED;
            b.reason = Reason.COMMON_SMALLER;
            return a;
        }
        if (ac && !bc) {
            a.reason = Reason.COMMON_LOSES_TO_DEF;
            b.reason = Reason.PROVIDED;
            return b;
        }
        if (!ac && bc) {
            b.reason = Reason.COMMON_LOSES_TO_DEF;
            a.reason = Reason.PROVIDED;
            return a;
        }
        if (a.bind == Elf.STB_WEAK && b.bind == Elf.STB_WEAK) {
            b.reason = Reason.ORDER_SHADOWED;
            a.reason = Reason.PROVIDED;
            return a;
        }
        if (a.bind == Elf.STB_WEAK && b.bind == Elf.STB_GLOBAL) {
            a.reason = Reason.WEAK_LOSES_TO_STRONG;
            b.reason = Reason.PROVIDED;
            return b;
        }
        if (a.bind == Elf.STB_GLOBAL && b.bind == Elf.STB_WEAK) {
            b.reason = Reason.WEAK_LOSES_TO_STRONG;
            a.reason = Reason.PROVIDED;
            return a;
        }
        // strong vs strong：交由最终裁决标记歧义；GST 先保留先到者
        b.reason = Reason.AMBIGUOUS;
        a.reason = Reason.AMBIGUOUS;
        return a;
    }

    private String describeClash(Candidate prev, Candidate now, Candidate win) {
        if (prev.common && now.common) return "common 合并，取对齐更大者";
        if (prev.common || now.common) return "真实定义覆盖 common";
        if (prev.bind == Elf.STB_WEAK || now.bind == Elf.STB_WEAK) return "强定义覆盖弱定义";
        return "多个强定义，保留歧义";
    }

    private void processShared(LinkUnit u) {
        u.included = true;
        event("INCLUDE", "遇到共享对象", u.objectName
                + (u.elf.soname != null ? " (SONAME " + u.elf.soname + ")" : ""));
        satisfyFromKnownDynamic();
        // 该共享库的导出对其之后的引用可用
        int added = 0;
        for (Sym s : u.elf.symbols) {
            if (!s.dynamic || !s.defined || s.bind == Elf.STB_LOCAL || s.type == Elf.STT_SECTION) {
                continue;
            }
            if (s.visibility == Elf.STV_HIDDEN) {
                continue;
            }
            added++;
        }
        if (added > 0) {
            event("SYMBOL_UPDATE", "动态提供者就位", u.objectName + " 导出 " + added + " 个全局符号");
        }
        // DSO 自己的动态未定义也进入未决
        for (Sym s : u.elf.symbols) {
            if (s.dynamic && !s.defined && s.bind != Elf.STB_LOCAL) {
                if (s.bind == Elf.STB_WEAK) {
                    pendingWeak.add(s.name);
                } else {
                    pendingStrong.add(s.name);
                }
            }
        }
        satisfyFromKnownDynamic();
    }

    /** 用已遇到的共享库满足当前未决引用（仅记录首个动态候选）。 */
    private void satisfyFromKnownDynamic() {
        for (LinkUnit u : result.units) {
            if (!u.included || u.elf == null || u.elf.type != Elf.ET_DYN) {
                continue;
            }
            for (Sym s : u.elf.symbols) {
                if (!s.dynamic || !s.defined || s.bind == Elf.STB_LOCAL
                        || s.type == Elf.STT_SECTION || s.visibility == Elf.STV_HIDDEN) {
                    continue;
                }
                if ((pendingStrong.contains(s.name) || pendingWeak.contains(s.name))
                        && !firstDynamic.containsKey(s.name)) {
                    Candidate c = dynamicCandidate(u, null, s);
                    firstDynamic.put(s.name, c);
                }
            }
        }
    }

    private Candidate dynamicCandidate(LinkUnit u, LinkUnit.Member m, Sym s) {
        Candidate c = new Candidate();
        c.objectName = u.objectName;
        c.memberName = null;
        c.provider = u.objectName;
        c.symbol = s;
        c.dynamic = true;
        c.bind = s.bind;
        c.visibility = s.visibility;
        VersionInfo vi = versionOf(u.elf, s);
        c.versionName = vi == null || vi.special ? null : vi.name;
        c.versionDefault = vi != null && vi.definedVersion && !vi.nonDefault;
        return c;
    }

    /**
     * 单遍扫描 archive：成员按归档顺序检查一次；后抽取成员的未定义只能拉动其后的成员。
     * repeat 策略下会反复调用直到定点。
     */
    private boolean scanArchive(LinkUnit u) {
        boolean changed = false;
        for (LinkUnit.Member m : new ArrayList<>(u.members)) {
            if (m.extractionAttempted) {
                continue;
            }
            String pull = null;
            boolean weak = false;
            for (String prov : m.provides) {
                if (pendingStrong.contains(prov)) {
                    pull = prov;
                    break;
                }
                if (pendingWeak.contains(prov)) {
                    pull = prov;
                    weak = true;
                }
            }
            if (pull == null || (weak && !result.policy.weakTriggersArchive)) {
                continue;
            }
            m.extractionAttempted = true;
            // 若该成员因 COMDAT 全组被去重且提供符号都在舍弃节中，则跳过抽取
            if (!memberStillProvides(u, m, pull)) {
                event("EXTRACT", "成员未抽取（COMDAT 已舍弃）",
                        u.objectName + "(" + m.name + ") 想提供 " + pull);
                continue;
            }
            m.pulledBy = pull;
            includeStatic(u, m, m.elf);
            changed = true;
        }
        return changed;
    }

    private boolean memberStillProvides(LinkUnit u, LinkUnit.Member m, String name) {
        for (Sym s : m.elf.symbols) {
            if (s.dynamic || !s.defined || !name.equals(s.name)) {
                continue;
            }
            if (!inDiscardedComdat(u, m, m.elf, s)) {
                return true;
            }
        }
        return false;
    }

    private String label(Candidate c) {
        return c.memberName != null ? c.objectName + "(" + c.memberName + ")" : c.objectName;
    }

    private String describe(Candidate c) {
        StringBuilder sb = new StringBuilder();
        if (c.versionName != null) {
            sb.append(" @").append(c.versionDefault ? "@" : "").append(c.versionName);
        }
        if (c.common) {
            sb.append(" common(align=").append(c.symbol.value).append(")");
        }
        if (c.visibility != null && c.visibility != Elf.STV_DEFAULT) {
            sb.append(" ").append(Elf.visibilityName(c.visibility));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 收集引用并裁决
    // ------------------------------------------------------------------

    private void collectAndDecide() {
        Map<String, Reference> refs = new LinkedHashMap<>();
        for (LinkUnit u : result.units) {
            if ("ERROR".equals(u.kind)) {
                continue;
            }
            if (u.archive) {
                for (LinkUnit.Member m : u.members) {
                    if (m.extracted) {
                        collectRefs(u, m, m.elf, refs);
                    }
                }
            } else if (u.included && u.elf != null) {
                collectRefs(u, null, u.elf, refs);
            }
        }
        for (Reference ref : refs.values()) {
            decideReference(ref);
            result.references.add(ref);
        }
    }

    private void collectRefs(LinkUnit u, LinkUnit.Member m, ElfFile f, Map<String, Reference> refs) {
        Set<Long> seen = new LinkedHashSet<>();
        for (Reloc rel : f.relocations) {
            if (rel.type == 0 || rel.symIndex == 0) {
                continue;
            }
            Sym s = f.symbols.stream()
                    .filter(x -> x.index == rel.symIndex && x.dynamic == rel.allocated
                            && !x.defined && x.type != Elf.STT_SECTION)
                    .findFirst().orElse(null);
            if (s == null) {
                // 非动态重定位优先查普通 symtab
                s = f.symbolAt(rel.symIndex);
                if (s != null && (s.defined || s.type == Elf.STT_SECTION)) {
                    s = null;
                }
            }
            if (s == null) {
                continue;
            }
            VersionInfo vi = versionOf(f, s);
            String reqVer = vi == null || vi.special ? null : vi.name;
            String key = (m != null ? u.objectName + "\0" + m.name : u.objectName + "\0")
                    + "\0" + s.name + "\0" + (reqVer == null ? "" : reqVer)
                    + "\0" + (s.bind == Elf.STB_WEAK);
            Reference ref = refs.computeIfAbsent(key, k -> {
                Reference r = new Reference();
                r.objectName = u.objectName;
                r.memberName = m == null ? null : m.name;
                r.symName = s.name;
                r.bind = s.bind;
                r.requiredVersion = reqVer;
                r.versionedRef = reqVer != null;
                r.dynamicRef = f.type == Elf.ET_DYN;
                return r;
            });
            rel.symName = s.name;
            if (seen.add((long) rel.sectionName.hashCode() * 31 + rel.offset)) {
                ref.relocations.add(rel);
            }
        }
    }

    private boolean positionEligible(int sourceOrder, LinkUnit candidate) {
        return candidate.order > sourceOrder
                || (candidate.order == sourceOrder && !candidate.archive);
    }

    private void decideReference(Reference ref) {
        int sourceOrder = -1;
        for (LinkUnit u : result.units) {
            if (u.objectName.equals(ref.objectName)) {
                sourceOrder = u.order;
                break;
            }
        }
        List<Candidate> statics = new ArrayList<>();
        List<Candidate> dynamics = new ArrayList<>();
        List<Candidate> archives = new ArrayList<>();
        boolean anySameNameVersionAnywhere = false;
        boolean sameNameVersionOfferedByDso = false;

        for (LinkUnit u : result.units) {
            if ("ERROR".equals(u.kind)) {
                continue;
            }
            if (u.archive) {
                for (LinkUnit.Member m : u.members) {
                    for (Sym s : m.elf.symbols) {
                        if (s.dynamic || !s.defined || s.type == Elf.STT_SECTION
                                || !ref.symName.equals(s.name)) {
                            continue;
                        }
                        Candidate c = memberCandidate(u, m, m.elf, s);
                        anySameNameVersionAnywhere = true;
                        if (m.extracted) {
                            (c.staticDef ? statics : archives).add(c);
                        } else {
                            archives.add(c);
                        }
                        if (ref.versionedRef && c.versionName != null
                                && ref.requiredVersion.equals(c.versionName)) {
                            sameNameVersionOfferedByDso = false;
                        }
                    }
                }
                continue;
            }
            if (u.elf == null) {
                continue;
            }
            boolean isDyn = u.elf.type == Elf.ET_DYN;
            for (Sym s : u.elf.symbols) {
                if (!ref.symName.equals(s.name) || s.type == Elf.STT_SECTION || s.bind == Elf.STB_LOCAL) {
                    continue;
                }
                if (!isDyn && s.dynamic) {
                    continue;
                }
                if (isDyn && (!s.dynamic || !s.defined)) {
                    continue;
                }
                if (!isDyn && !s.defined) {
                    continue;
                }
                VersionInfo vi = versionOf(u.elf, s);
                String ver = vi == null || vi.special ? null : vi.name;
                anySameNameVersionAnywhere = true;

                Candidate c = new Candidate();
                c.objectName = u.objectName;
                c.provider = u.objectName;
                c.symbol = s;
                c.bind = s.bind;
                c.visibility = s.visibility;
                c.common = s.common;
                c.versionName = ver;
                c.versionDefault = vi != null && vi.definedVersion && !vi.nonDefault;
                if (isDyn) {
                    c.dynamic = true;
                    boolean eligible = u.included && positionEligible(sourceOrder, u);
                    if (s.visibility == Elf.STV_HIDDEN) {
                        c.reason = Reason.HIDDEN_NOT_EXPORTED;
                        c.detail = "共享库 HIDDEN 符号不参与跨对象解析";
                    } else if (!eligible) {
                        c.reason = Reason.DYNAMIC_AT_REFERENCE;
                        c.detail = "引用产生时该共享库尚未出现在加载序列";
                    } else if (ref.versionedRef && ver != null
                            && !ref.requiredVersion.equals(ver)) {
                        c.reason = Reason.VERSION_MISMATCH;
                        c.detail = "引用需要 " + ref.requiredVersion + "，候选为 " + ver;
                        sameNameVersionOfferedByDso = true;
                    } else {
                        c.reason = Reason.PROVIDED;
                        dynamics.add(c);
                    }
                    ref.candidates.add(c);
                } else {
                    c.staticDef = true;
                    if (inDiscardedComdat(u, null, u.elf, s)) {
                        c.reason = Reason.COMDAT_DISCARDED;
                        c.detail = "所在 COMDAT 副本被去重舍弃";
                        c.comdatDiscarded = true;
                    } else if (s.visibility == Elf.STV_HIDDEN && u.elf.type == Elf.ET_REL) {
                        if (!ref.objectName.equals(u.objectName)) {
                            c.reason = Reason.HIDDEN_NOT_EXPORTED;
                            c.detail = "HIDDEN 符号仅在定义对象内可见";
                        } else {
                            statics.add(c);
                        }
                    } else if (ref.versionedRef && ver != null
                            && !ref.requiredVersion.equals(ver)) {
                        c.reason = Reason.VERSION_MISMATCH;
                        c.detail = "引用需要 " + ref.requiredVersion + "，候选为 " + ver;
                    } else {
                        statics.add(c);
                    }
                    ref.candidates.add(c);
                }
            }
        }

        chooseWinner(ref, statics, dynamics, archives, sourceOrder,
                anySameNameVersionAnywhere, sameNameVersionOfferedByDso);
    }

    private Candidate memberCandidate(LinkUnit u, LinkUnit.Member m, ElfFile f, Sym s) {
        Candidate c = new Candidate();
        c.objectName = u.objectName;
        c.memberName = m.name;
        c.provider = f.name;
        c.symbol = s;
        c.bind = s.bind;
        c.visibility = s.visibility;
        c.common = s.common;
        VersionInfo vi = versionOf(f, s);
        c.versionName = vi == null || vi.special ? null : vi.name;
        c.versionDefault = vi != null && vi.definedVersion && !vi.nonDefault;
        return c;
    }

    private void chooseWinner(Reference ref, List<Candidate> statics, List<Candidate> dynamics,
                              List<Candidate> archiveCandidates, int sourceOrder,
                              boolean anySameName, boolean sameNameOtherVersionInDso) {
        // 归档成员候选分类与原因
        for (Candidate c : archiveCandidates) {
            LinkUnit u = unitOf(c.objectName);
            LinkUnit.Member m = u == null ? null
                    : u.members.stream().filter(mm -> mm.name.equals(c.memberName))
                        .findFirst().orElse(null);
            ref.candidates.add(c);
            if (m != null && m.extracted) {
                c.staticDef = true;
                if (inDiscardedComdat(u, m, m.elf, c.symbol)) {
                    c.comdatDiscarded = true;
                    c.reason = Reason.COMDAT_DISCARDED;
                } else if (ref.versionedRef && c.versionName != null
                        && !ref.requiredVersion.equals(c.versionName)) {
                    c.reason = Reason.VERSION_MISMATCH;
                    c.detail = "引用需要 " + ref.requiredVersion + "，候选为 " + c.versionName;
                } else {
                    statics.add(c);
                }
                continue;
            }
            if (m != null && inDiscardedComdat(u, m, m.elf, c.symbol)) {
                c.comdatDiscarded = true;
                c.reason = Reason.COMDAT_DISCARDED;
                continue;
            }
            if (c.visibility != null && c.visibility == Elf.STV_HIDDEN) {
                c.reason = Reason.HIDDEN_NOT_EXPORTED;
                c.detail = "成员内 HIDDEN 定义不可见";
                continue;
            }
            if (ref.versionedRef && c.versionName != null
                    && !ref.requiredVersion.equals(c.versionName)) {
                c.reason = Reason.VERSION_MISMATCH;
                c.detail = "引用需要 " + ref.requiredVersion + "，候选为 " + c.versionName;
                continue;
            }
            boolean needed = isNeededBySequence(c, sourceOrder);
            c.archiveExtracted = false;
            c.reason = needed ? Reason.ARCHIVE_NOT_EXTRACTED : Reason.ARCHIVE_NOT_NEEDED;
            c.detail = needed
                    ? "单遍扫描结束时引用仍未决：循环依赖需要重复组扫描"
                    : "该成员出现位置之前没有需要它的未决引用";
        }

        Candidate winner = null;
        List<Candidate> strongs = new ArrayList<>();
        Candidate bestCommon = null;
        Candidate weak = null;
        for (Candidate c : statics) {
            if (c.common) {
                if (bestCommon == null || c.symbol.value > bestCommon.symbol.value) {
                    bestCommon = c;
                }
            } else if (c.bind == Elf.STB_WEAK) {
                if (weak == null) {
                    weak = c;
                }
            } else {
                strongs.add(c);
            }
        }
        if (strongs.size() > 1) {
            ref.ambiguous = true;
            for (Candidate c : strongs) {
                c.reason = Reason.AMBIGUOUS;
                c.detail = "存在 " + strongs.size() + " 个同等强定义，加载顺序不用于裁决";
            }
            ref.outcome = Reason.AMBIGUOUS;
            ref.outcomeDetail = "重复候选: " + describeList(strongs);
            ref.winner = null;
            markOtherCandidates(ref);
            return;
        }
        if (strongs.size() == 1) {
            winner = strongs.get(0);
            if (bestCommon != null) {
                bestCommon.reason = Reason.COMMON_LOSES_TO_DEF;
                bestCommon.detail = "common 让位于强定义";
            }
            if (weak != null) {
                weak.reason = Reason.WEAK_LOSES_TO_STRONG;
            }
        } else if (bestCommon != null) {
            winner = bestCommon;
            if (weak != null) {
                weak.reason = Reason.WEAK_LOSES_TO_STRONG;
                weak.detail = "弱定义让位于 common 合并结果";
            }
        } else if (weak != null) {
            winner = weak;
        }

        Candidate dynWinner = null;
        if (winner == null) {
            for (Candidate c : dynamics) {
                if (c.bind == Elf.STB_GLOBAL) {
                    dynWinner = c;
                    break;
                }
            }
            if (dynWinner == null && !dynamics.isEmpty()) {
                dynWinner = dynamics.get(0);
            }
            if (dynamics.stream().filter(c -> c.bind == Elf.STB_GLOBAL).count() > 1) {
                // 共享库之间以显式加载顺序裁决，不构成歧义
                List<Candidate> rest = dynamics.stream()
                        .filter(c -> c.bind == Elf.STB_GLOBAL).skip(1).toList();
                for (Candidate c : rest) {
                    c.reason = Reason.ORDER_SHADOWED;
                    c.detail = "显式加载顺序中被更早的共享库定义遮蔽";
                }
            }
            winner = dynWinner;
        } else {
            for (Candidate c : dynamics) {
                c.reason = Reason.ORDER_SHADOWED;
                c.detail = "静态定义优先于动态绑定";
            }
        }

        if (winner == null) {
            handleUnresolved(ref, anySameName, sameNameOtherVersionInDso);
        } else {
            ref.winner = winner;
            winner.reason = Reason.PROVIDED;
            winner.detail = "";
            applyCopyRelocation(ref);
            ref.outcome = ref.copyRelocation ? Reason.COPY_RELOCATION : Reason.PROVIDED;
            ref.outcomeDetail = ref.copyRelocation
                    ? "可执行文件对数据对象生成 copy relocation，运行期复制该数据"
                    : "绑定到 " + winner.label() + describe(winner);
        }
        markOtherCandidates(ref);
    }

    private boolean isNeededBySequence(Candidate c, int sourceOrder) {
        LinkUnit u = unitOf(c.objectName);
        if (u == null) {
            return false;
        }
        // 引用在 archive 之前出现且到序列结束都未被满足 => 循环/顺序问题
        if (sourceOrder >= 0 && u.order < sourceOrder) {
            return true;
        }
        // archive 在引用之前但成员未抽取，且最终仍未决：多半是循环依赖
        return true;
    }

    private LinkUnit unitOf(String name) {
        for (LinkUnit u : result.units) {
            if (u.objectName.equals(name)) {
                return u;
            }
        }
        return null;
    }

    private String describeList(List<Candidate> cs) {
        List<String> labels = new ArrayList<>();
        for (Candidate c : cs) {
            labels.add(c.label());
        }
        return String.join(", ", labels);
    }

    private void markOtherCandidates(Reference ref) {
        for (Candidate c : ref.candidates) {
            if (c == ref.winner) {
                continue;
            }
            if (c.reason == null) {
                c.reason = Reason.ORDER_SHADOWED;
                c.detail = "未被选为最终提供者";
            }
        }
    }

    private void handleUnresolved(Reference ref, boolean anySameName, boolean otherVersionInDso) {
        // 非默认版本只能被显式版本引用使用：检查候选里是否有同名同提供者但缺版本
        boolean unmet = ref.versionedRef
                && ref.candidates.stream().anyMatch(c -> c.versionName == null
                        || (!c.versionName.equals(ref.requiredVersion)
                                && (c.reason == Reason.VERSION_MISMATCH)));
        if (unmet || otherVersionInDso) {
            ref.unmetVersion = true;
            ref.outcome = Reason.VERSION_UNMET;
            ref.outcomeDetail = "没有提供者提供版本 " + ref.requiredVersion;
            if (ref.bind == Elf.STB_WEAK) {
                ref.nullWeak = true;
            }
            return;
        }
        if (ref.bind == Elf.STB_WEAK) {
            ref.nullWeak = true;
            ref.outcome = Reason.WEAK_ALLOW_NULL;
            ref.outcomeDetail = "弱引用允许为 0，不产生链接错误";
            return;
        }
        ref.outcome = ref.versionedRef ? Reason.VERSION_UNMET : Reason.WRONG_ARTIFACT;
        ref.outcomeDetail = ref.versionedRef
                ? "未满足的版本需求: " + ref.requiredVersion
                : (anySameName ? "存在同名符号但均不满足引用条件" : "无任何候选提供者");
    }

    private void applyCopyRelocation(Reference ref) {
        if (ref.winner == null || !ref.winner.dynamic) {
            return;
        }
        Sym s = ref.winner.symbol;
        boolean dataObject = s.type == Elf.STT_OBJECT || s.type == Elf.STT_NOTYPE;
        boolean fromExec = false;
        for (LinkUnit u : result.units) {
            if (u.objectName.equals(ref.objectName) && u.isExec) {
                fromExec = true;
            }
        }
        // 可执行文件直接引用数据对象（绝对重定位）时，传统 ELF 预测为 copy relocation
        boolean absoluteReloc = ref.relocations.stream()
                .anyMatch(r -> r.typeName.endsWith("_64") || r.typeName.endsWith("_32")
                        || r.typeName.endsWith("COPY") || r.typeName.endsWith("GLOB_DAT"));
        if (fromExec && dataObject && absoluteReloc
                && !isPositionIndependentExec() ) {
            if (ref.winner.visibility == Elf.STV_PROTECTED
                    && !result.policy.allowProtectedCopy) {
                ref.winner.reason = Reason.PROTECTED_NO_COPY;
                ref.winner.detail = "目标平台禁止对 PROTECTED 符号做 copy relocation";
                ref.outcome = Reason.PROTECTED_NO_COPY;
                ref.outcomeDetail = "copy relocation 与 PROTECTED 冲突: " + ref.winner.label();
                ref.winner = null;
                return;
            }
            ref.copyRelocation = true;
        }
    }

    private boolean isPositionIndependentExec() {
        // fixture 不构造 PIE；保留策略钩子
        return false;
    }

    // ------------------------------------------------------------------
    // 模拟地址布局（确定性，供方案比较区分“换提供者”与“仅地址变化”）
    // ------------------------------------------------------------------

    private void assignSimulatedAddresses() {
        long execBase = 0x400000L;
        long dynBase = 0x7f0000000000L;
        Map<String, Long> baseOf = new LinkedHashMap<>();
        for (LinkUnit u : result.units) {
            if ("ERROR".equals(u.kind)) {
                continue;
            }
            if (u.archive) {
                for (LinkUnit.Member m : u.members) {
                    if (m.extracted) {
                        long b = execBase;
                        execBase += imageSize(m.elf) + 0x1000;
                        baseOf.put(u.objectName + "\0" + m.name, b);
                    }
                }
            } else if (u.included && u.elf != null) {
                if (u.elf.type == Elf.ET_DYN) {
                    baseOf.put(u.objectName + "\0", dynBase);
                    dynBase += Math.max(imageSize(u.elf), 0x100000L) + 0x100000L;
                } else {
                    baseOf.put(u.objectName + "\0", execBase);
                    execBase += imageSize(u.elf) + 0x1000;
                }
            }
        }
        for (Reference ref : result.references) {
            if (ref.winner == null) {
                continue;
            }
            String key = ref.winner.memberName != null
                    ? ref.winner.objectName + "\0" + ref.winner.memberName
                    : ref.winner.objectName + "\0";
            long base = baseOf.getOrDefault(key, 0L);
            ref.winner.simulatedAddress = base + (ref.winner.symbol == null ? 0 : ref.winner.symbol.value);
            for (Candidate c : ref.candidates) {
                if (c.symbol == null) {
                    continue;
                }
                String ck = c.memberName != null ? c.objectName + "\0" + c.memberName
                        : c.objectName + "\0";
                c.simulatedAddress = baseOf.getOrDefault(ck, 0L) + c.symbol.value;
            }
            ref.simulatedAddress = ref.winner.simulatedAddress;
        }
    }

    private long imageSize(ElfFile f) {
        long max = 0;
        for (com.symbolcourt.model.Section s : f.sections) {
            if (s.type != Elf.SHT_NOBITS) {
                max = Math.max(max, s.offset + s.size);
            } else if (s.addr != 0) {
                max = Math.max(max, s.addr + s.size);
            }
        }
        return Math.max(max, 0x2000);
    }

    private void finishEvents() {
        long unres = result.references.stream()
                .filter(r -> r.winner == null && !r.nullWeak && !r.ambiguous).count();
        long amb = result.references.stream().filter(r -> r.ambiguous).count();
        long copies = result.references.stream().filter(r -> r.copyRelocation).count();
        event("DONE", "裁决完成",
                "引用 " + result.references.size() + " 条；未决 " + unres
                        + "；歧义 " + amb + "；copy relocation " + copies);
    }

    private void finalizeEventsForReference(Reference ref) {
        Event e = new Event(step++, "RESOLVE", "裁决引用: " + ref.sourceLabel() + " -> " + ref.symName,
                ref.outcome == null ? "" : ref.outcome.label + (ref.outcomeDetail.isEmpty() ? "" : "（" + ref.outcomeDetail + "）"));
        e.snapshot.addAll(events.isEmpty() ? List.of() : events.get(events.size() - 1).snapshot);
        events.add(e);
        result.events = events;
    }
}
