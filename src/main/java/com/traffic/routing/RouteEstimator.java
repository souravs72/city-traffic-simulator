package com.traffic.routing;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.Objects;
import java.util.Optional;

/** Estimates travel ticks (sum of baseWeights) for a routed path. */
public final class RouteEstimator {

    private RouteEstimator() {
    }

    public record Estimate(int hops, int travelTicks) {
    }

    public static Optional<Estimate> estimate(
            RoadGraph graph,
            Router router,
            NodeId from,
            NodeId to,
            EdgeCost cost
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(cost, "cost");
        if (from.equals(to)) {
            return Optional.of(new Estimate(0, 0));
        }
        Optional<Path> path = router.findPath(graph, from, to, cost);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromPath(graph, path.get()));
    }

    public static Estimate fromPath(RoadGraph graph, Path path) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(path, "path");
        int ticks = 0;
        for (EdgeId edgeId : path.edges()) {
            ticks += graph.requireEdge(edgeId).baseWeight();
        }
        return new Estimate(path.hopCount(), ticks);
    }
}
