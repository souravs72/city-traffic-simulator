package com.traffic.routing;

import com.traffic.model.graph.Edge;

/**
 * Cost of traversing an edge right now.
 * Today: baseWeight. Later: congestion / closures can plug in here.
 */
@FunctionalInterface
public interface EdgeCost {

    int cost(Edge edge);

    static EdgeCost baseWeight() {
        return Edge::baseWeight;
    }
}
