package com.traffic.routing;

import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.Optional;

/** Finds a path on a road graph. Swap Dijkstra / A* via this interface. */
public interface Router {

    Optional<Path> findPath(RoadGraph graph, NodeId start, NodeId goal, EdgeCost cost);
}
