package com.traffic.model.traffic;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.RoadGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Live occupancy and temporary closures. Topology stays in {@link RoadGraph}.
 */
public final class TrafficState {

    private final RoadGraph graph;
    private final Map<EdgeId, Integer> occupancy = new HashMap<>();
    private final Set<EdgeId> closed = new HashSet<>();

    public TrafficState(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public RoadGraph graph() {
        return graph;
    }

    public int occupancy(EdgeId edgeId) {
        return occupancy.getOrDefault(edgeId, 0);
    }

    public boolean isClosed(EdgeId edgeId) {
        return closed.contains(edgeId);
    }

    /** Accident / construction: edge cannot be entered until reopened. */
    public void close(EdgeId edgeId) {
        graph.requireEdge(edgeId);
        closed.add(edgeId);
    }

    public void reopen(EdgeId edgeId) {
        closed.remove(edgeId);
    }

    public boolean hasCapacity(EdgeId edgeId) {
        Edge edge = graph.requireEdge(edgeId);
        return occupancy(edgeId) < edge.capacity();
    }

    public boolean canEnter(EdgeId edgeId) {
        return !isClosed(edgeId) && hasCapacity(edgeId);
    }

    /** Enter if open and under capacity. Returns false if blocked or full. */
    public boolean tryEnter(EdgeId edgeId) {
        if (!canEnter(edgeId)) {
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
