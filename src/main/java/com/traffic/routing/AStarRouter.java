package com.traffic.routing;

import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.Objects;
import java.util.Optional;

/**
 * A*: Dijkstra + heuristic. Optimal when the heuristic is admissible
 * (never overestimates remaining cost).
 */
public final class AStarRouter implements Router {

    private final Router delegate;

    public AStarRouter(Heuristic heuristic) {
        this.delegate = new ShortestPathRouter(Objects.requireNonNull(heuristic, "heuristic"));
    }

    @Override
    public Optional<Path> findPath(RoadGraph graph, NodeId start, NodeId goal, EdgeCost cost) {
        return delegate.findPath(graph, start, goal, cost);
    }
}
