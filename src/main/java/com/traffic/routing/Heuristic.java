package com.traffic.routing;

import com.traffic.model.graph.NodeId;

/**
 * Estimated remaining cost from {@code from} to {@code goal} (does not traverse edges).
 * Must be admissible (never overestimate) for A* to stay optimal.
 */
@FunctionalInterface
public interface Heuristic {

    int estimate(NodeId from, NodeId goal);

    /** h = 0 → A* behaves exactly like Dijkstra. */
    static Heuristic zero() {
        return (from, goal) -> 0;
    }
}
