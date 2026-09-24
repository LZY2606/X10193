package com.symtribunal.engine;

import java.util.ArrayList;
import java.util.List;

public class VersionNode {
    public String id;
    public String unitKey;
    public String label;
    public String kind;
    public String name;
    public boolean defaultVersion;
    public boolean weak;
    public List<String> aliases = new ArrayList<>();
    public List<String> symbols = new ArrayList<>();
}
