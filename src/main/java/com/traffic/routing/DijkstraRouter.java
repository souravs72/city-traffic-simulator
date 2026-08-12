package com.traffic.routing;

import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.Optional;

/** Dijkstra: optimal shortest path using only edge costs (heuristic = 0). */
public final class DijkstraRouter implements Router {

    private final Router delegate = new ShortestPathRouter(Heuristic.zero());

    @Override
    public Optional<Path> findPath(RoadGraph graph, NodeId start, NodeId goal, EdgeCost cost) {
        return delegate.findPath(graph, start, goal, cost);
    }
}
