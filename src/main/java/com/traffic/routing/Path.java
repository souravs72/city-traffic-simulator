package com.traffic.routing;

import com.traffic.model.graph.EdgeId;

import java.util.List;
import java.util.Objects;

/** A route: ordered edges from start to goal, plus total cost. */
public record Path(List<EdgeId> edges, int totalCost) {

    public Path {
        Objects.requireNonNull(edges, "edges");
        edges = List.copyOf(edges);
        if (totalCost < 0) {
            throw new IllegalArgumentException("totalCost must be >= 0");
        }
    }

    public boolean isEmpty() {
        return edges.isEmpty();
    }

    public int hopCount() {
        return edges.size();
    }
}
