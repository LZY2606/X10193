package com.symtribunal.engine;

import java.util.ArrayList;
import java.util.List;

/** A group of relocations/undefined uses of one (name[, version]) from one unit. */
public class Reference {
    public String key;
    public String unitKey;
    public String unitLabel;
    public String name;
    public String requiredVersion;
    public boolean weakReference;
    public boolean copyReference;
    public String status;
    public String winnerUnitKey;
    public String winnerVersion;
    public Long address;
    public String note;
    public List<RelocView> relocations = new ArrayList<>();
    public List<Candidate> candidates = new ArrayList<>();
}
