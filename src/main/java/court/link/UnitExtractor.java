package court.link;

import court.elf.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts a parsed ELF image into the engine's neutral model: providers (definitions)
 * and references (undefined symbols / dynamic relocations), restoring every version
 * index to its version name and recording COMDAT membership.
 */
@Component
public class UnitExtractor {

    private static final class VersionInfo {
        Map<Integer, String> defName = new HashMap<>();
        Map<Integer, Boolean> defNonDefault = new HashMap<>();
        Map<Integer, String> needName = new HashMap<>();
        Set<String> definedVersionNames = new HashSet<>();
    }

    public UnitModel extract(LoadedBinary bin, int inputOrder) {
        ElfImage img = bin.image;
        UnitModel u = new UnitModel();
        u.unitId = bin.unitId;
        u.displayName = bin.displayName();
        u.sourceFile = bin.sourceFile;
        u.memberName = bin.memberName;
        u.inputOrder = inputOrder;
        u.fromArchive = bin.memberName != null;
        u.corrupt = bin.corrupt;
        u.error = bin.error;
        u.image = img;
        if (img == null || img.corrupt) return u;

        u.kind = img.typeName();
        u.machine = img.machine;
        u.needed.addAll(img.needed);
        u.soname = img.soname;

        VersionInfo vi = buildVersions(img);

        Map<Integer, String> groupSignatureBySection = new HashMap<>();
        Map<Integer, ElfSection> groupOfSection = new HashMap<>();
        buildComdat(img, groupSignatureBySection, groupOfSection);

        // Providers + undefined references from the full symbol table when present,
        // otherwise the dynamic symbol table.
        java.util.List<ElfSymbol> table = img.symbols.isEmpty() ? img.dynSymbols : img.symbols;
        boolean dynamicTable = table == img.dynSymbols;
        Set<String> seenProvider = new HashSet<>();
        for (ElfSymbol s : table) {
            if (s.name == null || s.name.isEmpty() || s.type() == Const.STT_SECTION || s.type() == Const.STT_FILE) continue;
            if (s.binding() == Const.STB_LOCAL) continue; // locals never enter global arbitration
            if (s.isUndef()) {
                Reference r = baseRef(u, s, vi, dynamicTable);
                r.fromRelocation = false;
                u.references.add(r);
            } else {
                Provider p = toProvider(u, s, vi, groupSignatureBySection, groupOfSection);
                String dedup = p.name + "\u0000" + p.version + "\u0000" + p.sectionIndex;
                if (seenProvider.add(dedup)) u.providers.add(p);
            }
        }

        // Dynamic relocations are explicit references even when the dynsym itself
        // happens to hold a same-named definition (PLT/copy machinery).
        addRelocationReferences(u, img, vi);
        return u;
    }

    private VersionInfo buildVersions(ElfImage img) {
        VersionInfo vi = new VersionInfo();
        for (VersionDef d : img.versionDefs) {
            for (String n : d.allNames()) {
                vi.defName.put(d.index, n);
                vi.defNonDefault.put(d.index, true);
                vi.definedVersionNames.add(n);
            }
            // the base/anonymous definition means "version 1" default unversioned too
            if (d.base()) vi.defName.put(d.index, "");
        }
        for (VersionNeed vn : img.versionNeeds) {
            for (VersionNeed.Entry e : vn.entries) {
                vi.needName.put(e.index, e.name);
            }
        }
        return vi;
    }

    private void buildComdat(ElfImage img, Map<Integer, String> sig, Map<Integer, ElfSection> groupOf) {
        for (ElfSection g : img.sections) {
            if (!g.isComdat()) continue;
            ElfSection symSec = img.sectionByIndex(g.link);
            ElfSymbol signature = null;
            if (symSec != null) {
                java.util.List<ElfSymbol> t = symSec.type == Const.SHT_DYNSYM ? img.dynSymbols : img.symbols;
                if (g.info >= 0 && g.info < t.size()) signature = t.get(g.info);
            }
            String name = signature != null && signature.name != null ? signature.name : ("grp" + g.index);
            for (Integer memberIdx : g.groupMembers) {
                sig.put(memberIdx, name);
            }
        }
    }

    private Provider toProvider(UnitModel u, ElfSymbol s, VersionInfo vi,
                                Map<Integer, String> comdatSig, Map<Integer, ElfSection> groupOf) {
        ElfImage img = u.image;
        Provider p = new Provider();
        p.unitId = u.unitId;
        p.displayName = u.displayName;
        p.sourceFile = u.sourceFile;
        p.name = s.name;
        p.binding = Const.bindingName(s.binding());
        p.visibility = Const.visibilityName(s.visibility());
        p.type = Const.typeName(s.type());
        p.size = s.size;
        p.sectionIndex = s.shndx;
        ElfSection sec = img.sectionByIndex(s.shndx);
        p.sectionName = sec != null ? sec.name : ("shndx=" + s.shndx);
        p.comdatSignature = comdatSig.get(s.shndx);

        String[] ver = resolveDefVersion(s, vi);
        p.version = ver[0];
        p.nonDefault = Boolean.parseBoolean(ver[1]);

        if (s.isCommon()) {
            p.definitionClass = "COMMON";
            p.interposable = false;
        } else {
            p.definitionClass = img.type == Const.ET_DYN ? "SHARED_DEF" : "OBJECT_DEF";
            // A shared definition can be preempted only when exported with default visibility.
            p.interposable = p.definitionClass.equals("SHARED_DEF")
                    && s.visibility() == Const.STV_DEFAULT
                    && s.binding() != Const.STB_LOCAL
                    && !p.nonDefault;
        }
        return p;
    }

    /** Returns {versionName, nonDefault}. */
    private String[] resolveDefVersion(ElfSymbol s, VersionInfo vi) {
        int vi2 = s.versionIndex;
        if (!s.dynamic) return new String[]{"", "false"};
        if (vi2 == Const.VER_NDX_LOCAL) return new String[]{"", "false"};
        if (vi2 == Const.VER_NDX_GLOBAL) return new String[]{"", "false"};
        String name = vi.defName.get(vi2);
        if (name != null) {
            return new String[]{name, "true"};
        }
        // a definition dynsym carrying a needed index is unusual; treat by name
        String need = vi.needName.get(vi2);
        if (need != null) return new String[]{need, "true"};
        return new String[]{"<v" + vi2 + ">", "true"};
    }

    private Reference baseRef(UnitModel u, ElfSymbol s, VersionInfo vi, boolean dynamicTable) {
        Reference r = new Reference();
        r.unitId = u.unitId;
        r.displayName = u.displayName;
        r.name = s.name;
        r.binding = Const.bindingName(s.binding());
        r.visibility = Const.visibilityName(s.visibility());
        r.version = resolveNeedVersion(s, vi);
        r.nonDefaultNeeded = s.dynamic && s.versionIndex > 1;
        return r;
    }

    private String resolveNeedVersion(ElfSymbol s, VersionInfo vi) {
        if (!s.dynamic) return "";
        int idx = s.versionIndex;
        if (idx <= 1) return "";
        String need = vi.needName.get(idx);
        if (need != null) return need;
        String def = vi.defName.get(idx);
        if (def != null) return def;
        return "<v" + idx + ">";
    }

    private void addRelocationReferences(UnitModel u, ElfImage img, VersionInfo vi) {
        RelTypes.Policy policy = RelTypes.policy(img.machine);
        for (ElfRelocation r : img.relocations) {
            java.util.List<ElfSymbol> t = pickSymTab(img, r);
            if (r.symbolIndex == 0 || r.symbolIndex >= t.size()) continue;
            ElfSymbol s = t.get(r.symbolIndex);
            if (s == null || s.name == null || s.name.isEmpty()) continue;
            Reference ref;
            if (s.isUndef()) {
                ref = baseRef(u, s, vi, t == img.dynSymbols);
            } else {
                // relocation against a locally defined symbol (e.g. copy in the executable)
                ref = baseRef(u, s, vi, t == img.dynSymbols);
                ref.binding = Const.bindingName(s.binding());
            }
            ref.fromRelocation = true;
            ref.relocOffset = r.offset;
            ref.relocSection = r.sectionName;
            ref.addend = r.addend;
            ref.relocType = r.computedType;
            ref.copyRelocation = policy.isCopy(r.type);
            ref.jumpSlot = policy.isJumpSlot(r.type);
            ref.globDat = policy.isGlobDat(r.type);
            if (policy.isRelative(r.type)) continue;
            u.references.add(ref);
        }
    }

    private java.util.List<ElfSymbol> pickSymTab(ElfImage img, ElfRelocation r) {
        ElfSection rs = img.sectionByName(r.sectionName);
        ElfSection link = rs != null ? img.sectionByIndex(rs.link) : null;
        if (link != null && link.type == Const.SHT_SYMTAB) return img.symbols;
        return img.dynSymbols;
    }
}
