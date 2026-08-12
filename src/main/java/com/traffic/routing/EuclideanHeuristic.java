package com.traffic.routing;

import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.Objects;

/**
 * Euclidean distance heuristic from node coordinates.
 * Admissible when every edge weight is at least the geometric length of that edge
 * (e.g. weights ≈ distance in the same units).
 */
public final class EuclideanHeuristic implements Heuristic {

    private final RoadGraph graph;

    public EuclideanHeuristic(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @Override
    public int estimate(NodeId from, NodeId goal) {
        if (from.equals(goal)) {
            return 0;
        }
        Node a = graph.requireNode(from);
        Node b = graph.requireNode(goal);
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return (int) Math.floor(Math.hypot(dx, dy));
    }
}
