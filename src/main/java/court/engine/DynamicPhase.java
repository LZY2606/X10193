package court.engine;

import court.elf.ElfConst;
import court.elf.ElfObject;
import court.elf.Reloc;
import court.elf.Sym;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dynamic link phase. Candidates are exported dynamic symbols in explicit load
 * order; versions, visibility, protected binding and copy relocations are
 * applied as distinct rules, never folded into plain override semantics.
 */
final class DynamicPhase {

    void harvestDynamic(LinkResult r, Unit u) {
        ElfObject elf = u.elf;
        if (elf == null) return;
        for (Sym s : elf.dynSyms) {
            if (s.name == null || s.name.isEmpty()) continue;
            if (s.bind == ElfConst.STB_LOCAL) continue;
            boolean hiddenBit = (s.rawVersionIndex & ElfConst.VERSYM_HIDDEN) != 0;
            boolean undef = s.shndx == ElfConst.SHN_UNDEF;
            if (undef) {
                Ref ref = Linker.ensureRef(r, u, s, null, false);
                ref.bind = s.bind;
                ref.weak = s.bind == ElfConst.STB_WEAK;
                continue;
            }
            Def d = Linker.baseDef(u, s, true);
            d.defined = true;
            d.common = false;
            boolean hiddenVis = s.visibility == ElfConst.STV_HIDDEN
                    || s.visibility == ElfConst.STV_INTERNAL;
            if (hiddenVis) {
                d.live = false;
                d.eliminatedReason = "hidden/内部可见性，不进入动态导出";
                d.eliminationStage = "EXPORT";
            }
            // VERSYM_HIDDEN marks a non-default version: still exported to
            // versioned references, hidden only from unversioned lookup.
            if (s.visibility == ElfConst.STV_PROTECTED) {
                d.comdatGroup = "PROTECTED";
            }
            u.defs.add(d);
        }
        for (Reloc rel : elf.relocs) {
            if (rel.symbol == null || rel.symbol.name == null
                    || rel.symbol.name.isEmpty()) continue;
            Sym s = rel.symbol;
            if (s.bind == ElfConst.STB_LOCAL) continue;
            // A relocation against a protected definition in this same object is
            // a local (non-interposable) reference even when the dynsym table
            // also carries an undefined placeholder with the same name.
            boolean internal = s.shndx != ElfConst.SHN_UNDEF
                    && s.visibility == ElfConst.STV_PROTECTED;
            Ref ref = null;
            if (internal) {
                String iid = Linker.refId(u, s.name, s.dynamic ? s.versionName : null, true);
                for (Ref cand : u.refs) if (cand.id.equals(iid)) { ref = cand; break; }
            }
            if (ref == null) ref = Linker.ensureRef(r, u, s, null, internal);
            ref.internal = internal || ref.internal;
            ref.bind = s.bind;
            ref.weak = s.bind == ElfConst.STB_WEAK;
            Linker.attach(ref, rel);
        }
    }

    void run(LinkResult r, Policy policy) {
        List<Def> exported = new ArrayList<>();
        for (Unit u : r.units) {
            if (u.elf == null || !Linker.isDynamicKind(u)) continue;
            for (Def d : u.defs) {
                if (d.defined && d.live) exported.add(d);
            }
        }
        exported.sort(Comparator.comparingLong(d -> d.order));

        // All references that still need a dynamic decision.
        List<Ref> pending = new ArrayList<>();
        for (Unit u : r.units) {
            if (u.elf == null) continue;
            boolean dynamicUnit = Linker.isDynamicKind(u);
            for (Ref ref : u.refs) {
                if (ref.internal) continue;
                Res existing = ResTable.get(r).get(Res.keyOf(ref));
                if (!dynamicUnit && existing != null && "RESOLVED".equals(existing.status)) {
                    continue; // static phase already bound
                }
                pending.add(ref);
            }
        }

        for (Ref ref : pending) {
            adjudicate(r, policy, ref, exported);
        }
        bindInternal(r, policy);
    }

    private void bindInternal(LinkResult r, Policy policy) {
        for (Unit u : r.units) {
            if (u.elf == null || !Linker.isDynamicKind(u)) continue;
            for (Ref ref : u.refs) {
                if (!ref.internal) continue;
                Res res = ResTable.get(r).computeIfAbsent(Res.keyOf(ref),
                        k -> new Res(k, ref.name, ref.version));
                if (!res.refs.contains(ref)) res.refs.add(ref);
                Def local = null;
                for (Def d : u.defs) {
                    if (d.defined && d.live && d.name.equals(ref.name)
                            && (ref.version == null || ref.version.equals(d.version))) {
                        local = d;
                        break;
                    }
                }
                if (local != null) {
                    res.winner = local;
                    res.status = "RESOLVED";
                    res.stage = "PROTECTED_LOCAL";
                    res.detail = "protected/本地符号在本对象内直接绑定（不插入）";
                    res.address = "0x" + Long.toHexString(local.value);
                    Linker.addStep(r, "PROTECTED_LOCAL", "本地绑定 " + ref.name,
                            u.name + " 内部引用直接绑定本对象定义", u.id, ref.name);
                } else {
                    res.status = ref.weak ? "UNRESOLVED_WEAK" : "UNRESOLVED";
                    res.detail = "内部引用无对应定义";
                }
            }
        }
    }

    private void adjudicate(LinkResult r, Policy policy, Ref ref, List<Def> exported) {
        Res res = ResTable.get(r).computeIfAbsent(Res.keyOf(ref),
                k -> new Res(k, ref.name, ref.version));
        if ("AMBIGUOUS".equals(res.status)) return;
        if (!res.refs.contains(ref)) res.refs.add(ref);
        res.stage = "DYNAMIC";

        List<Def> sameName = new ArrayList<>();
        for (Def d : exported) {
            if (d.name.equals(ref.name)) sameName.add(d);
        }
        List<Def> versionOk = new ArrayList<>();
        for (Def d : sameName) {
            String why = versionReject(r, ref, d);
            if (why != null) {
                Elim(r, res, d, why, "VERSION");
            } else {
                versionOk.add(d);
            }
        }
        List<Def> visible = new ArrayList<>();
        for (Def d : versionOk) {
            if (!d.live) {
                Elim(r, res, d, "hidden 符号不导出", "EXPORT");
            } else {
                visible.add(d);
            }
        }
        res.dynamicCandidates = visible;

        // Copy relocations: executable takes a private copy; that definition
        // intercepts later lookups instead of following normal override order.
        boolean anyCopy = ref.copyReloc && policy.copyRelocEnabled;
        if (anyCopy) {
            Def dataTarget = null;
            for (Def d : visible) {
                if (isData(d)) { dataTarget = d; break; }
            }
            if (dataTarget != null) {
                if (dataTarget.visibility == ElfConst.STV_PROTECTED
                        && policy.protectedMode == Policy.ProtectedMode.ERROR) {
                    res.status = "ERROR";
                    res.detail = "copy relocation 针对 protected 数据符号 "
                            + dataTarget.unitName + "（平台策略拒绝）";
                    noteCopyStep(r, ref, dataTarget, "COPY_PROTECTED_ERROR");
                    return;
                }
                Def copy = makeCopyDefinition(ref, dataTarget);
                res.winner = copy;
                res.status = "RESOLVED";
                res.detail = "copy relocation：在 " + ref.unitName
                        + " 建立私有副本，截获自 " + dataTarget.unitName;
                noteCopyStep(r, ref, dataTarget, "COPY_RELOC");
                return;
            }
        }

        if (visible.isEmpty()) {
            if (ref.version != null) {
                res.status = "ERROR";
                res.detail = "未满足版本需求 " + ref.name + "@" + ref.version
                        + (ref.requiredFromFile != null ? "（需要来自 " + ref.requiredFromFile + "）" : "");
                Linker.addStep(r, "VERSION_MISS", "未满足版本 " + ref.name,
                        res.detail, ref.unitId, ref.name);
            } else {
                res.status = ref.weak ? "UNRESOLVED_WEAK" : "UNRESOLVED";
                res.detail = ref.weak ? "weak 引用无提供者，绑定为 0" : "动态阶段无提供者";
            }
            return;
        }

        // Explicit load order decides; ties at identical rank are kept ambiguous.
        List<Def> strong = new ArrayList<>();
        List<Def> weak = new ArrayList<>();
        for (Def d : visible) {
            if (d.bind == ElfConst.STB_WEAK) weak.add(d); else strong.add(d);
        }
        List<Def> pool = strong.isEmpty() ? weak : strong;
        Def first = pool.get(0);
        boolean tie = pool.size() > 1 && pool.get(1).order == first.order;
        if (tie && policy.tieMode == Policy.TieMode.AMBIGUOUS) {
            res.status = "AMBIGUOUS";
            res.ambiguity = "DYNAMIC_DUPLICATE_RANK";
            res.detail = "多个同等顺序候选，保留歧义";
            res.winner = null;
            return;
        }
        // Record losers with explicit reasons.
        for (Def d : pool.subList(1, pool.size())) {
            Elim(r, res, d, "加载顺序靠后（" + d.unitName + " 在 " + first.unitName
                    + " 之后），被前序定义截获", "ORDER");
        }
        for (Def d : (strong.isEmpty() ? List.<Def>of() : weak)) {
            Elim(r, res, d, "weak 定义不截获 strong 查找", "BINDING");
        }

        // Protected binding policy for a reference originating from another DSO.
        if (first.visibility == ElfConst.STV_PROTECTED && !first.unitId.equals(ref.unitId)) {
            switch (policy.protectedMode) {
                case LOCAL -> {
                    // External reference cannot bind the protected definition
                    // itself; report the policy outcome explicitly.
                    res.status = "ERROR";
                    res.detail = "外部引用无法绑定 protected 符号 " + first.unitName
                            + "（受保护符号不允许插入）";
                    Linker.addStep(r, "PROTECTED_REJECT", "protected 拒绝跨对象绑定 "
                            + ref.name, res.detail, first.unitId, ref.name);
                    return;
                }
                case ERROR -> {
                    res.status = "ERROR";
                    res.detail = "protected 符号 " + ref.name + " 被跨对象引用（严格策略报错）";
                    return;
                }
                case PREEMPTIBLE -> { /* fall through: treat as normal */ }
            }
        }

        res.winner = first;
        res.status = "RESOLVED";
        res.detail = strong.isEmpty()
                ? "仅 weak 导出，绑定 " + first.unitName
                : "加载顺序中首个 strong 导出 " + first.unitName;
        res.address = "0x" + Long.toHexString(first.value);
        Step st = Linker.addStep(r, "BIND", "绑定 " + ref.name,
                ref.unitName + " -> " + first.unitName
                        + (ref.version != null ? " [" + ref.version + "]" : ""),
                ref.unitId, ref.name);
        st.data.put("winner", first.unitName);
        st.symbolTable = Linker.snapshot(r, true);
    }

    private boolean isData(Def d) {
        if (d.sym == null) return false;
        int t = d.sym.type;
        return t == ElfConst.STT_OBJECT || t == ElfConst.STT_COMMON || t == ElfConst.STT_NOTYPE;
    }

    private Def makeCopyDefinition(Ref ref, Def source) {
        Def copy = new Def();
        copy.unitId = ref.unitId;
        copy.unitName = ref.unitName + " (copy)";
        copy.order = -1L;
        copy.name = ref.name;
        copy.version = source.version;
        copy.bind = ElfConst.STB_GLOBAL;
        copy.visibility = ElfConst.STV_DEFAULT;
        copy.defined = true;
        copy.size = source.size;
        copy.value = 0;
        copy.copyDefinition = true;
        copy.sectionName = ".bss (copy)";
        return copy;
    }

    private void noteCopyStep(LinkResult r, Ref ref, Def source, String kind) {
        Step s = Linker.addStep(r, kind, kind.equals("COPY_RELOC")
                ? "copy relocation " + ref.name : "protected copy 冲突 " + ref.name,
                ref.unitName + " 引用 -> 私有拷贝自 " + source.unitName, ref.unitId, ref.name);
        s.data.put("source", source.unitName);
        s.data.put("size", source.size);
    }

    /**
     * Version gate: version indices have already been restored to names.
     * Non-default versions never satisfy an unversioned lookup; versioned
     * references require the exact version definition.
     */
    private String versionReject(LinkResult r, Ref ref, Def d) {
        String dname = d.version;
        boolean nonDefault = d.sym != null
                && (d.sym.rawVersionIndex & ElfConst.VERSYM_HIDDEN) != 0;
        if (ref.version == null) {
            if (dname == null) return null;
            if (nonDefault) {
                return "非默认版本 " + dname + " 不满足无版本引用";
            }
            return null;
        }
        if (dname == null) {
            return "提供者无版本，无法满足版本需求 @" + ref.version;
        }
        if (!dname.equals(ref.version) && !aliasMatches(r, d, ref.version)) {
            return "版本不匹配：提供者为 " + dname + "，需求为 " + ref.version;
        }
        return null;
    }

    private boolean aliasMatches(LinkResult r, Def d, String wanted) {
        if (d.sym == null) return false;
        ElfObject obj = unitElf(r, d);
        if (obj == null) return false;
        int idx = d.sym.rawVersionIndex & 0x7fff;
        ElfObject.VerDefInfo vd = obj.verDefs.get(idx);
        if (vd == null) return false;
        if (wanted.equals(vd.name)) return true;
        for (String alias : vd.aliases) {
            if (wanted.equals(alias)) return true;
        }
        return false;
    }

    private ElfObject unitElf(LinkResult r, Def d) {
        for (Unit u : r.units) {
            if (u.id.equals(d.unitId)) return u.elf;
        }
        return null;
    }

    private void Elim(LinkResult r, Res res, Def d, String why, String stage) {
        Res.Elim e = new Res.Elim();
        e.def = d;
        e.reason = why;
        e.stage = stage;
        res.eliminations.add(e);
    }
}
