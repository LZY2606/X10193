package court.engine;

import court.elf.Sym;

/**
 * A single symbol definition provided by one unit. Two Defs with the same symbol
 * name are still distinct candidates: unit, section, version and binding differ.
 */
public final class Def {
    public Def() {}
    public String unitId;
    public String unitName;
    public long order;
    public Sym sym;
    public String name;
    public String version;
    public int bind;
    public int visibility;
    public boolean common;
    public boolean defined;
    public boolean live = true;
    public boolean dormant;
    public String comdatGroup;
    public String sectionName;
    public long value;
    public long size;
    public boolean copyDefinition;
    public String eliminatedReason;
    public String eliminationStage;
}
