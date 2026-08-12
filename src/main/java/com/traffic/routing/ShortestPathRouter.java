package com.traffic.routing;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Shared shortest-path engine: A* with an injectable heuristic.
 * Dijkstra is this class with {@link Heuristic#zero()}.
 */
final class ShortestPathRouter implements Router {

    private final Heuristic heuristic;

    ShortestPathRouter(Heuristic heuristic) {
        this.heuristic = Objects.requireNonNull(heuristic, "heuristic");
    }

    @Override
    public Optional<Path> findPath(RoadGraph graph, NodeId start, NodeId goal, EdgeCost cost) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(cost, "cost");

        graph.requireNode(start);
        graph.requireNode(goal);

        if (start.equals(goal)) {
            return Optional.of(new Path(List.of(), 0));
        }

        Map<NodeId, Integer> gScore = new HashMap<>();
        Map<NodeId, Edge> cameFrom = new HashMap<>();
        gScore.put(start, 0);

        PriorityQueue<FrontierNode> open = new PriorityQueue<>(Comparator.comparingInt(FrontierNode::f));
        open.add(new FrontierNode(start, heuristic.estimate(start, goal), 0));

        while (!open.isEmpty()) {
            FrontierNode current = open.poll();
            int knownG = gScore.getOrDefault(current.id(), Integer.MAX_VALUE);
            if (current.g() > knownG) {
                continue; // stale queue entry
            }
            if (current.id().equals(goal)) {
                return Optional.of(reconstruct(cameFrom, goal, knownG));
            }

            for (Edge edge : graph.outgoing(current.id())) {
                int step = cost.cost(edge);
                if (step < 0) {
                    throw new IllegalArgumentException("EdgeCost must be non-negative, got " + step);
                }
                int tentativeG = knownG + step;
                NodeId neighbor = edge.to();
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, edge);
                    gScore.put(neighbor, tentativeG);
                    int f = tentativeG + heuristic.estimate(neighbor, goal);
                    open.add(new FrontierNode(neighbor, f, tentativeG));
                }
            }
        }

        return Optional.empty();
    }

    private static Path reconstruct(Map<NodeId, Edge> cameFrom, NodeId goal, int totalCost) {
        List<EdgeId> edges = new ArrayList<>();
        NodeId cursor = goal;
        while (cameFrom.containsKey(cursor)) {
            Edge edge = cameFrom.get(cursor);
            edges.add(edge.id());
            cursor = edge.from();
        }
        Collections.reverse(edges);
        return new Path(edges, totalCost);
    }

    private record FrontierNode(NodeId id, int f, int g) {
    }
}
