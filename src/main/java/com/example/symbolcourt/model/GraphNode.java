package com.example.symbolcourt.model;

public class GraphNode {
    public String id;
    public String label;
    public String kind; // OBJECT / SHARED / EXECUTABLE / ARCHIVE / VERSION

    public GraphNode(String id, String label, String kind) {
        this.id = id;
        this.label = label;
        this.kind = kind;
    }
}
