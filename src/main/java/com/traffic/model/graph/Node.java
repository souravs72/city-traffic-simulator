package com.traffic.model.graph;

import java.util.Objects;

/** An intersection on the map (topology only). Coordinates support A* heuristics. */
public record Node(NodeId id, String label, double x, double y) {
    public Node {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }
}
