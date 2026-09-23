package com.symbolcourt.linker;

import com.symbolcourt.model.ElfFile;

import java.util.ArrayList;
import java.util.List;

/** 一次方案裁决的完整结果。 */
public class ResolutionResult {
    public List<LinkUnit> units = new ArrayList<>();
    public List<Reference> references = new ArrayList<>();
    public List<Event> events = new ArrayList<>();
    public Policy policy = new Policy();
    public List<String> warnings = new ArrayList<>();
    public List<String> errors = new ArrayList<>();
    public List<ComdatGroup> comdatGroups = new ArrayList<>();
    public long generatedAt;

    /** 参与最终链接的全部 ELF（含被抽取成员），供依赖图/版本图展示。 */
    public List<ElfFile> participatingElfs() {
        List<ElfFile> out = new ArrayList<>();
        for (LinkUnit u : units) {
            if (u.archive) {
                for (LinkUnit.Member m : u.members) {
                    if (m.extracted) {
                        out.add(m.elf);
                    }
                }
            } else if (u.elf != null) {
                out.add(u.elf);
            }
        }
        return out;
    }

    public List<String> unresolvedStrong() {
        List<String> out = new ArrayList<>();
        for (Reference ref : references) {
            if (ref.winner == null && !ref.nullWeak && ref.bind == com.symbolcourt.elf.Elf.STB_GLOBAL) {
                out.add(ref.sourceLabel() + ": " + ref.symName
                        + (ref.versionedRef ? "@" + ref.requiredVersion : ""));
            }
        }
        return out;
    }

    public static class ComdatGroup {
        public String name;
        public String chosenObject;
        public String chosenMember;
        public List<String> discarded = new ArrayList<>();
    }
}
