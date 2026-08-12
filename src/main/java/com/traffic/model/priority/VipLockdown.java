package com.traffic.model.priority;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.routing.RoutingAlgorithm;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * VIP lockdown that stays strong without trapping the city.
 * <ul>
 *   <li>Hard-close the VIP spine (exclusive lane)</li>
 *   <li>Soft-tax junction approaches + a 2-hop buffer (most nearby roads discouraged)</li>
 *   <li>Optionally promote junction edges to hard only when a civilian bypass still exists</li>
 * </ul>
 */
public final class VipLockdown {

    public record Plan(Set<EdgeId> hardClosed, Set<EdgeId> softBuffer) {
        public Plan {
            hardClosed = Set.copyOf(hardClosed);
            softBuffer = Set.copyOf(softBuffer);
        }
    }

    private VipLockdown() {
    }

    public static Plan plan(RoadGraph graph, List<EdgeId> vipPath) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(vipPath, "vipPath");
        if (vipPath.isEmpty()) {
            return new Plan(Set.of(), Set.of());
        }

        Set<EdgeId> spine = new HashSet<>(vipPath);
        Set<NodeId> pathNodes = new HashSet<>();
        for (EdgeId id : vipPath) {
            Edge e = graph.requireEdge(id);
            pathNodes.add(e.from());
            pathNodes.add(e.to());
        }

        Set<EdgeId> junction = new HashSet<>();
        for (Edge edge : graph.edges()) {
            if (spine.contains(edge.id())) {
                continue;
            }
            if (pathNodes.contains(edge.from()) || pathNodes.contains(edge.to())) {
                junction.add(edge.id());
            }
        }

        Set<EdgeId> hard = new HashSet<>(spine);
        // Promote junction clearance to hard when the graph still has a bypass.
        Set<EdgeId> trialHard = new HashSet<>(spine);
        trialHard.addAll(junction);
        if (hasBypass(graph, vipPath, trialHard)) {
            hard = trialHard;
        }

        Set<EdgeId> soft = new HashSet<>();
        if (!hard.containsAll(junction)) {
            soft.addAll(junction);
        }

        // 2-hop soft buffer around path nodes — "most nearby roads discouraged"
        Set<NodeId> ring = new HashSet<>(pathNodes);
        for (EdgeId id : soft) {
            Edge e = graph.requireEdge(id);
            ring.add(e.from());
            ring.add(e.to());
        }
        for (Edge edge : graph.edges()) {
            if (hard.contains(edge.id()) || soft.contains(edge.id())) {
                continue;
            }
            if (ring.contains(edge.from()) || ring.contains(edge.to())) {
                soft.add(edge.id());
            }
        }

        // Second hop
        Set<NodeId> ring2 = new HashSet<>();
        for (EdgeId id : soft) {
            Edge e = graph.requireEdge(id);
            ring2.add(e.from());
            ring2.add(e.to());
        }
        for (Edge edge : graph.edges()) {
            if (hard.contains(edge.id()) || soft.contains(edge.id())) {
                continue;
            }
            if (ring2.contains(edge.from()) || ring2.contains(edge.to())) {
                soft.add(edge.id());
            }
        }

        soft.removeAll(hard);
        return new Plan(hard, soft);
    }

    private static boolean hasBypass(RoadGraph graph, List<EdgeId> vipPath, Set<EdgeId> hard) {
        Edge first = graph.requireEdge(vipPath.get(0));
        Edge last = graph.requireEdge(vipPath.get(vipPath.size() - 1));
        NodeId start = first.from();
        NodeId goal = last.to();
        Router router = Routers.create(RoutingAlgorithm.DIJKSTRA, graph);
        EdgeCost avoidHard = edge -> hard.contains(edge.id()) ? 1_000_000 : edge.baseWeight();
        Optional<Path> bypass = router.findPath(graph, start, goal, avoidHard);
        return bypass.isPresent() && !bypass.get().edges().isEmpty()
                && bypass.get().totalCost() < 1_000_000;
    }
}
