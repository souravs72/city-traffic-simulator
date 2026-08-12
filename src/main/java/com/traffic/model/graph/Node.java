package com.traffic.model.graph;

import java.util.Objects;

/** An intersection on the map (topology + optional facility designation). */
public record Node(NodeId id, String label, double x, double y, FacilityKind facility) {
    public Node {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (facility == null) {
            facility = FacilityKind.NONE;
        }
    }

    public Node(NodeId id, String label, double x, double y) {
        this(id, label, x, y, FacilityKind.NONE);
    }
}
