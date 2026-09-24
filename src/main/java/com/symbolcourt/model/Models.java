package com.symbolcourt.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain data model for parsed ELF objects, symbol arbitration inputs and results. */
public final class Models {
    private Models() {}

    public enum Kind { OBJECT, SHARED, EXECUTABLE, ARCHIVE, ARCHIVE_MEMBER }

    public static class VersionDef {
        public int index;
        public int flags;
        public boolean base;
        public String name;
        public List<String> aliases = new ArrayList<>();
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", index); m.put("name", name);
            m.put("flags", flags); m.put("base", base); m.put("aliases", aliases);
            return m;
        }
    }

    public static class VersionNeed {
        public int index;
        public int flags;
        public String file;
        public String name;
        public boolean weak;
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", index); m.put("name", name); m.put("file", file);
            m.put("flags", flags); m.put("weak", weak);
            return m;
        }
    }

    public static class ElfSymbol {
        public int index;
        public String name = "";
        public int bind;
        public int type;
        public int visibility;
        public long value;
        public long size;
        public int shndx;
        public boolean dynamic;
        public int versionIndex = -1;
        public String versionName;
        public boolean versionDefault;
        public boolean versionHiddenFlag;
        public boolean undefined;
        public boolean common;
        public boolean local;
        public String comdatGroup;
        public long commonAlignment = 1;

        public String bindingName() {
            return switch (bind) {
                case 0 -> "LOCAL";
                case 1 -> "GLOBAL";
                case 2 -> "WEAK";
                default -> "BIND_" + bind;
            };
        }
        public String visibilityName() {
            return switch (visibility) {
                case 0 -> "DEFAULT";
                case 1 -> "INTERNAL";
                case 2 -> "HIDDEN";
                case 3 -> "PROTECTED";
                default -> "VIS_" + visibility;
            };
        }
        public String typeName() {
            return switch (type) {
                case 0 -> "NOTYPE";
                case 1 -> "OBJECT";
                case 2 -> "FUNC";
                case 3 -> "SECTION";
                case 4 -> "FILE";
                case 5 -> "COMMON";
                case 6 -> "TLS";
                default -> "TYPE_" + type;
            };
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", index); m.put("name", name);
            m.put("binding", bindingName()); m.put("type", typeName());
            m.put("visibility", visibilityName());
            m.put("value", value); m.put("size", size);
            m.put("section", shndx);
            m.put("dynamic", dynamic);
            m.put("undefined", undefined); m.put("common", common);
            m.put("versionIndex", versionIndex);
            m.put("versionName", versionName);
            m.put("versionDefault", versionDefault);
            m.put("comdatGroup", comdatGroup);
            return m;
        }
    }

    public static class Relocation {
        public String section;
        public long offset;
        public int type;
        public String typeName;
        public long addend;
        public int symbolIndex;
        public String symbolName;
        public boolean copyRelocation;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", section); m.put("offset", offset);
            m.put("type", type); m.put("typeName", typeName);
            m.put("addend", addend); m.put("symbolIndex", symbolIndex);
            m.put("symbolName", symbolName);
            m.put("copyRelocation", copyRelocation);
            return m;
        }
    }

    public static class ComdatGroup {
        public int sectionIndex;
        public String signature;
        public boolean comdat;
        public List<Integer> members = new ArrayList<>();
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sectionIndex); m.put("signature", signature);
            m.put("comdat", comdat); m.put("members", members);
            return m;
        }
    }

    public static class ParsedObject {
        public String id;
        public String name;
        public String origin;
        public Kind kind;
        public String arch = "x86_64";
        public int elfClass = 64;
        public boolean littleEndian = true;
        public String soname;
        public List<String> needed = new ArrayList<>();
        public List<ElfSymbol> symbols = new ArrayList<>();
        public List<Relocation> relocations = new ArrayList<>();
        public List<VersionDef> versionDefs = new ArrayList<>();
        public List<VersionNeed> versionNeeds = new ArrayList<>();
        public List<ComdatGroup> groups = new ArrayList<>();
        public List<String> sections = new ArrayList<>();
        public List<String> diagnostics = new ArrayList<>();
        /** for archive members: containing archive id and member name. */
        public String archiveId;
        public String memberName;
        public int memberIndex;

        public boolean isShared() { return kind == Kind.SHARED || kind == Kind.ARCHIVE_MEMBER; }
    }
}
