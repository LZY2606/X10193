package court.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import court.elf.ElfSymbol;

/**
 * 一个被选中/候选的定义提供者。providerId 在方案内稳定，用于跨方案语义比较。
 * 同时提供引擎用的（LoadUnit, ElfSymbol）构造器与快照反序列化用的全参构造器。
 */
public final class Provider {
    public final String providerId;
    public final String unitId;
    public final String unitName;
    public final int unitOrdinal;
    public final int symbolIndex;
    public final String symbol;
    public final String bind;
    public final String visibility;
    public final String type;
    public final String versionName;
    public final boolean nonDefaultVersion;
    public final boolean definition;
    public final long value;
    public final long size;
    public final boolean fromSharedObject;
    public final boolean common;
    public final boolean comdat;
    public final String comdatSignature;
    public final boolean copyRelocation;

    public Provider(LoadUnit unit, ElfSymbol sym, boolean common, boolean comdat,
                    String comdatSignature, boolean copyRelocation) {
        this(unit.unitId, unit.name, unit.ordinal, sym.index, sym.name,
                sym.bindLabel(), sym.visibilityLabel(), sym.typeLabel(),
                sym.version != null ? sym.version.name : null,
                sym.version != null && sym.version.nonDefault,
                sym.version == null || sym.version.definition,
                sym.value, sym.size,
                unit.elf != null && unit.elf.isSharedObject(),
                common, comdat, comdatSignature, copyRelocation);
    }

    @JsonCreator
    public Provider(
            @JsonProperty("unitId") String unitId,
            @JsonProperty("unitName") String unitName,
            @JsonProperty("unitOrdinal") int unitOrdinal,
            @JsonProperty("symbolIndex") int symbolIndex,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("bind") String bind,
            @JsonProperty("visibility") String visibility,
            @JsonProperty("type") String type,
            @JsonProperty("versionName") String versionName,
            @JsonProperty("nonDefaultVersion") boolean nonDefaultVersion,
            @JsonProperty("definition") boolean definition,
            @JsonProperty("value") long value,
            @JsonProperty("size") long size,
            @JsonProperty("fromSharedObject") boolean fromSharedObject,
            @JsonProperty("common") boolean common,
            @JsonProperty("comdat") boolean comdat,
            @JsonProperty("comdatSignature") String comdatSignature,
            @JsonProperty("copyRelocation") boolean copyRelocation) {
        this.unitId = unitId;
        this.unitName = unitName;
        this.unitOrdinal = unitOrdinal;
        this.symbolIndex = symbolIndex;
        this.symbol = symbol;
        this.bind = bind;
        this.visibility = visibility;
        this.type = type;
        this.versionName = versionName;
        this.nonDefaultVersion = nonDefaultVersion;
        this.definition = definition;
        this.value = value;
        this.size = size;
        this.fromSharedObject = fromSharedObject;
        this.common = common;
        this.comdat = comdat;
        this.comdatSignature = comdatSignature;
        this.copyRelocation = copyRelocation;
        this.providerId = unitId + "#" + symbolIndex;
    }
}
