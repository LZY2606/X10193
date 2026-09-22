package com.example.symbolcourt.model;

public class GraphEdge {
    public String from;
    public String to;
    public String kind; // NEEDED / CONTAINS / PROVIDES / NEEDS
    public String label;

    public GraphEdge(String from, String to, String kind, String label) {
        this.from = from;
        this.to = to;
        this.kind = kind;
        this.label = label;
    }
}
