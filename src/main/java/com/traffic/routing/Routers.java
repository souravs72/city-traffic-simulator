package com.traffic.routing;

import com.traffic.model.graph.RoadGraph;

import java.util.Objects;

/** Builds a {@link Router} from a chosen algorithm. */
public final class Routers {

    private Routers() {
    }

    public static Router create(RoutingAlgorithm algorithm, RoadGraph graph) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(graph, "graph");
        return switch (algorithm) {
            case DIJKSTRA -> new DijkstraRouter();
            case ASTAR -> new AStarRouter(new EuclideanHeuristic(graph));
        };
    }
}
