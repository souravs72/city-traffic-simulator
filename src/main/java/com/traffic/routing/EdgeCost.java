package com.traffic.routing;

import com.traffic.model.graph.Edge;

/**
 * Cost of traversing an edge right now.
 * Congestion / closures plug in via implementations.
 */
@FunctionalInterface
public interface EdgeCost {

    /** Sentinel for closed / forbidden edges. */
    int CLOSED = 1_000_000;

    int cost(Edge edge);

    static EdgeCost baseWeight() {
        return Edge::baseWeight;
    }
}
