package court.engine;

import court.elf.ElfConst;
import court.elf.ElfObject;
import court.elf.Reloc;
import court.elf.Sym;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The adjudication core. No external linker is invoked: symbol tables, versions,
 * COMDAT groups, lazy archive extraction and relocations are interpreted directly.
 */
public final class Linker {

    public LinkResult run(List<InputEntry> inputs, Policy policy) {
        LinkResult r = new LinkResult();
        r.policy = policy;
        r.inputs.addAll(inputs);

        Loader.Loaded loaded = new Loader().load(r, inputs);
        r.units.addAll(loaded.units);
        r.edges.addAll(loaded.edges);
        r.parsedElves.addAll(loaded.elves);
        addStep(r, "PLAN", "加载顺序", "共 " + inputs.size() + " 个显式输入，策略="
                + policy.presetName + "/" + policy.phase + "/" + policy.protectedMode
                + "/" + policy.tieMode, null, null);

        // COMDAT for direct relocatable objects is seen first, in load order.
        for (Unit u : r.units) {
            if (u.elf != null && !u.dormant && isStaticKind(u)) {
                processComdat(r, u);
            }
        }
        harvest(r);

        if (policy.phase != Policy.Phase.DYNAMIC) {
            new StaticPhase().run(r, policy);
        }
        if (policy.phase != Policy.Phase.STATIC) {
            new DynamicPhase().run(r, policy);
        }
        collectResolutions(r, policy);
        buildVersionGraphs(r);
        satisfyDependencyEdges(r);
        return r;
    }

    static boolean isStaticKind(Unit u) {
        return "ELF_REL".equals(u.kind) || "ARCHIVE_MEMBER".equals(u.kind);
    }

    static boolean isDynamicKind(Unit u) {
        return "ELF_DYN".equals(u.kind) || "ELF_EXEC".equals(u.kind);
    }

    // ---------------------------------------------------------------- harvest

    void harvest(LinkResult r) {
        for (Unit u : r.units) {
            if (u.elf == null) continue;
            if (isStaticKind(u)) {
                harvestStatic(r, u);
            } else if (isDynamicKind(u)) {
                new DynamicPhase().harvestDynamic(r, u);
            }
        }
    }

    private void harvestStatic(LinkResult r, Unit u) {
        ElfObject elf = u.elf;
        for (Sym s : elf.staticSyms) {
            if (s.name == null || s.name.isEmpty()) continue;
            if (s.bind == ElfConst.STB_LOCAL) continue;
            boolean undef = s.shndx == ElfConst.SHN_UNDEF;
            Def d = baseDef(u, s, false);
            if (undef) {
                ensureRef(r, u, s, null, false).bind = s.bind;
            } else {
                d.common = s.shndx == ElfConst.SHN_COMMON || s.type == ElfConst.STT_COMMON;
                d.defined = true;
                if (s.section != null && s.section.discarded) {
                    d.live = false;
                    d.eliminatedReason = "COMDAT 组被去重丢弃";
                    d.eliminationStage = "COMDAT";
                }
                u.defs.add(d);
            }
        }
        // Relocation sites attach to references (including against defined symbols).
        for (Reloc rel : elf.relocs) {
            if (rel.symbol == null || rel.symbol.name == null || rel.symbol.name.isEmpty()) {
                continue;
            }
            Sym s = rel.symbol;
            boolean internal = s.shndx != ElfConst.SHN_UNDEF;
            Ref ref = ensureRef(r, u, s, null, internal);
            attach(ref, rel);
        }
    }

    static Def baseDef(Unit u, Sym s, boolean dynamic) {
        Def d = new Def();
        d.unitId = u.id;
        d.unitName = u.name;
        d.order = (u.rankMajor << 32) | (u.rankMinor & 0xffffffffL);
        d.sym = s;
        d.name = s.name;
        d.bind = s.bind;
        d.visibility = s.visibility;
        d.version = s.versionName;
        d.sectionName = s.section != null ? s.section.name : null;
        d.value = s.value;
        d.size = s.size;
        d.dormant = u.dormant;
        return d;
    }

    static Ref ensureRef(LinkResult r, Unit u, Sym s, String forcedVersion, boolean internal) {
        String version = forcedVersion != null ? forcedVersion
                : (s.dynamic ? s.versionName : null);
        String id = refId(u, s.name, version, internal);
        Ref existing = findRef(r, id);
        if (existing != null) return existing;
        Ref ref = new Ref();
        ref.id = id;
        ref.unitId = u.id;
        ref.unitName = u.name;
        ref.name = s.name;
        ref.version = version;
        ref.bind = s.bind;
        ref.weak = s.bind == ElfConst.STB_WEAK;
        ref.internal = internal;
        if (s.dynamic && version != null) {
            ref.requiredFromFile = requiredFromFile(u.elf, s.rawVersionIndex);
        }
        u.refs.add(ref);
        return ref;
    }

    private static Ref findRef(LinkResult r, String id) {
        for (Unit u : r.units) {
            for (Ref ref : u.refs) if (ref.id.equals(id)) return ref;
        }
        return null;
    }

    static String refId(Unit u, String name, String version, boolean internal) {
        return u.id + "|" + (internal ? "INT:" : "EXT:") + name + "|"
                + (version == null ? "-" : version);
    }

    static String requiredFromFile(ElfObject elf, int rawVersionIndex) {
        int idx = rawVersionIndex & 0x7fff;
        for (ElfObject.VerNeed vn : elf.verNeeds) {
            for (ElfObject.VerNeed.Aux aux : vn.aux) {
                if (aux.vers == idx) return vn.file;
            }
        }
        return null;
    }

    static void attach(Ref ref, Reloc rel) {
        Ref.RelocSite site = new Ref.RelocSite();
        site.section = rel.section != null ? rel.section.name : null;
        site.offset = rel.offset;
        site.type = rel.typeName;
        site.addend = rel.addend;
        site.reloc = rel;
        ref.copyReloc |= rel.copyReloc;
        ref.relocs.add(site);
    }

    // ---------------------------------------------------------------- comdat

    void processComdat(LinkResult r, Unit u) {
        if (u.elf == null) return;
        Map<String, String> winners = ComdatState.get(r);
        for (ElfObject.Section sec : u.elf.sections) {
            if (sec.group == null) continue;
            ElfObject.Group g = sec.group;
            if (g.signature == null) continue;
            String winner = winners.get(g.signature);
            boolean comdat = (g.flags & ElfConst.GRP_COMDAT) != 0;
            if (!comdat) continue;
            if (winner == null) {
                winners.put(g.signature, u.name);
                addStep(r, "COMDAT_SELECT", "COMDAT 选中 " + g.signature,
                        u.name + " 保留组（首见）", u.id, g.signature);
            } else if (!winner.equals(u.name)) {
                discardGroup(r, u, g, winner);
            }
        }
    }

    private void discardGroup(LinkResult r, Unit u, ElfObject.Group g, String winner) {
        Unit.UnitDiscard d = new Unit.UnitDiscard();
        d.signature = g.signature;
        d.winnerUnit = winner;
        d.reason = "重复 COMDAT 组，首见者 " + winner + " 胜出";
        for (int idx : g.members) {
            if (idx >= 0 && idx < u.elf.sectionByIndex.size()) {
                ElfObject.Section mem = u.elf.sectionByIndex.get(idx);
                if (mem != null) {
                    mem.discarded = true;
                    d.sections.add(mem.name);
                }
            }
        }
        // The group section itself is also dropped from the link.
        for (ElfObject.Section sec : u.elf.sections) {
            if (sec.group == g) sec.discarded = true;
        }
        u.discards.add(d);
        addStep(r, "COMDAT_DISCARD", "COMDAT 去重 " + g.signature,
                u.name + " 的组成员 " + d.sections + " 被丢弃，保留 " + winner,
                u.id, g.signature);
    }

    // ---------------------------------------------------------------- steps

    static Step addStep(LinkResult r, String kind, String title, String detail,
                        String unitId, String symbol) {
        Step s = new Step(r.steps.size(), kind, title);
        s.detail = detail;
        s.unitId = unitId;
        s.symbol = symbol;
        r.steps.add(s);
        return s;
    }

    static Map<String, String> snapshot(LinkResult r, boolean dynamic) {
        Map<String, String> snap = new LinkedHashMap<>();
        for (Unit u : r.units) {
            for (Def d : u.defs) {
                if (!d.live || !d.defined) continue;
                if (dynamic && isStaticKind(u)) continue;
                String key = d.name + (d.version != null ? "@" + d.version : "");
                snap.putIfAbsent(key, d.unitName);
            }
        }
        return snap;
    }

    // ------------------------------------------------------- final assembly

    private void collectResolutions(LinkResult r, Policy policy) {
        java.util.Map<String, Res> table = ResTable.get(r);
        for (Res res : table.values()) {
            Resolution out = new Resolution();
            out.key = res.key;
            out.name = res.name;
            out.version = res.version;
            out.status = res.status;
            out.detail = res.detail;
            out.references.addAll(res.refs);
            if (res.winner != null) {
                out.winner = res.winner;
                out.winnerAddress = res.address != null ? res.address
                        : ("0x" + Long.toHexString(res.winner.value));
            }
            java.util.List<Def> all = new ArrayList<>();
            for (Def d : res.staticCandidates) if (!all.contains(d)) all.add(d);
            for (Def d : res.dynamicCandidates) if (!all.contains(d)) all.add(d);
            if (res.winner instanceof Def && !all.contains(res.winner)) all.add(res.winner);
            java.util.Map<Def, String> elim = new LinkedHashMap<>();
            for (Res.Elim e : res.eliminations) elim.putIfAbsent(e.def, e.reason);

            for (Def d : all) {
                Resolution.CandidateView v = new Resolution.CandidateView();
                v.def = d;
                v.chosen = res.winner == d;
                v.stage = d.copyDefinition ? "COPY"
                        : (Linker.isStaticKind(unitOf(r, d.unitId)) ? "STATIC" : "DYNAMIC");
                if (v.chosen) {
                    v.reason = res.detail;
                } else if (!d.live && d.eliminatedReason != null) {
                    v.reason = d.eliminatedReason;
                } else {
                    v.reason = elim.getOrDefault(d, "未选中："
                            + (res.detail != null ? res.detail : "其他候选优先"));
                }
                out.candidates.add(v);
            }
            r.resolutions.add(out);
        }
        r.resolutions.sort(Comparator.comparing((Resolution x) -> x.name)
                .thenComparing(x -> x.version == null ? "" : x.version));
    }

    private Unit unitOf(LinkResult r, String id) {
        for (Unit u : r.units) if (u.id.equals(id)) return u;
        return null;
    }

    private void buildVersionGraphs(LinkResult r) {
        for (Unit u : r.units) {
            if (u.elf == null || !isDynamicKind(u)) continue;
            ElfObject elf = u.elf;
            if (elf.verDefs.isEmpty() && elf.verNeeds.isEmpty()
                    && (elf.dynSymVersions == null || elf.dynSymVersions.length == 0)) {
                continue;
            }
            LinkResult.VersionGraph g = new LinkResult.VersionGraph();
            g.unitId = u.id;
            for (ElfObject.VerDefInfo vd : elf.verDefs.values()) {
                String id = "vd:" + vd.ndx;
                node(g, id, vd.name == null ? ("VER_" + vd.ndx) : vd.name,
                        vd.base ? "ALIAS" : "DEFINITION", false);
                for (String alias : vd.aliases) {
                    String aid = "vda:" + vd.ndx + ":" + alias;
                    node(g, aid, alias, "ALIAS", false);
                    edge(g, aid, id, "别名到版本节点");
                }
            }
            for (ElfObject.VerNeed vn : elf.verNeeds) {
                String fid = "vnf:" + vn.file;
                node(g, fid, vn.file, "NEED_FILE", false);
                for (ElfObject.VerNeed.Aux aux : vn.aux) {
                    String vid = "vn:" + vn.file + ":" + aux.name;
                    node(g, vid, aux.name, "NEED_VERSION", false);
                    edge(g, vid, fid, "版本需求来自");
                }
            }
            for (Sym s : elf.dynSyms) {
                int raw = s.rawVersionIndex;
                if (raw == 0 || raw == 1) continue;
                String vname = elf.versionName(raw);
                if (vname == null || s.shndx == ElfConst.SHN_UNDEF) continue;
                String sid = "sym:" + s.name;
                node(g, sid, s.name, "SYMBOL", (raw & 0x8000) != 0);
                String tid = "vd-name:" + vname;
                if (nodeExists(g, "vd-name:" + vname) == null) {
                    // find def node by name
                    String found = null;
                    for (LinkResult.VGNode n : g.nodes) {
                        if (n.label.equals(vname)) { found = n.id; break; }
                    }
                    if (found == null) {
                        node(g, tid, vname, "DEFINITION", false);
                        found = tid;
                    }
                    edge(g, sid, found, "定义版本");
                }
            }
            r.versionGraphs.put(u.id, g);
        }
    }

    private void node(LinkResult.VersionGraph g, String id, String label,
                      String kind, boolean hiddenVersion) {
        if (nodeExists(g, id) != null) return;
        LinkResult.VGNode n = new LinkResult.VGNode();
        n.id = id;
        n.label = label;
        n.kind = kind;
        n.hiddenVersion = hiddenVersion;
        g.nodes.add(n);
    }

    private LinkResult.VGNode nodeExists(LinkResult.VersionGraph g, String id) {
        for (LinkResult.VGNode n : g.nodes) if (n.id.equals(id)) return n;
        return null;
    }

    private void edge(LinkResult.VersionGraph g, String from, String to, String label) {
        for (LinkResult.VGEdge e : g.edges) {
            if (e.from.equals(from) && e.to.equals(to)) return;
        }
        LinkResult.VGEdge e = new LinkResult.VGEdge();
        e.from = from;
        e.to = to;
        e.label = label;
        g.edges.add(e);
    }

    private void satisfyDependencyEdges(LinkResult r) {
        java.util.Set<String> sonames = new java.util.HashSet<>();
        for (Unit u : r.units) {
            if (u.elf != null && u.elf.soname != null) sonames.add(u.elf.soname);
            sonames.add(u.name);
        }
        for (LinkResult.DependencyEdge e : r.edges) {
            if (e.kind.equals("NEEDED")) e.satisfied = sonames.contains(e.to);
        }
    }
}
