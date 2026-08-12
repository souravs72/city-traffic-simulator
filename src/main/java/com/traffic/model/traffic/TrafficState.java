package com.traffic.model.traffic;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.RoadGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Live occupancy on edges. Topology stays in {@link RoadGraph}; this is mutable traffic state.
 */
public final class TrafficState {

    private final RoadGraph graph;
    private final Map<EdgeId, Integer> occupancy = new HashMap<>();

    public TrafficState(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public RoadGraph graph() {
        return graph;
    }

    public int occupancy(EdgeId edgeId) {
        return occupancy.getOrDefault(edgeId, 0);
    }

    public boolean hasCapacity(EdgeId edgeId) {
        Edge edge = graph.requireEdge(edgeId);
        return occupancy(edgeId) < edge.capacity();
    }

    /** Enter if under capacity. Returns false if full (caller should wait). */
    public boolean tryEnter(EdgeId edgeId) {
        if (!hasCapacity(edgeId)) {
            return false;
        }
        occupancy.merge(edgeId, 1, Integer::sum);
        return true;
    }

    public void leave(EdgeId edgeId) {
        int current = occupancy(edgeId);
        if (current <= 0) {
            throw new IllegalStateException("Cannot leave edge with zero occupancy: " + edgeId);
        }
        if (current == 1) {
            occupancy.remove(edgeId);
        } else {
            occupancy.put(edgeId, current - 1);
        }
    }
}
