package com.traffic.routing;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.Objects;

/**
 * Euclidean heuristic scaled to stay admissible vs travel-time edge weights.
 * Falls back to zero heuristic when geometry and weights are incomparable.
 */
public final class EuclideanHeuristic implements Heuristic {

    private final RoadGraph graph;
    private final double scale;

    public EuclideanHeuristic(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.scale = computeAdmissibleScale(graph);
    }

    static double computeAdmissibleScale(RoadGraph graph) {
        double minRatio = Double.POSITIVE_INFINITY;
        boolean any = false;
        for (Edge edge : graph.edges()) {
            Node a = graph.requireNode(edge.from());
            Node b = graph.requireNode(edge.to());
            double geom = Math.hypot(a.x() - b.x(), a.y() - b.y());
            if (geom < 1e-9) {
                continue;
            }
            any = true;
            minRatio = Math.min(minRatio, edge.baseWeight() / geom);
        }
        if (!any || !Double.isFinite(minRatio) || minRatio <= 0) {
            return 0;
        }
        return minRatio * 0.999;
    }

    public boolean isZeroHeuristic() {
        return scale <= 0;
    }

    @Override
    public int estimate(NodeId from, NodeId goal) {
        if (from.equals(goal) || scale <= 0) {
            return 0;
        }
        Node a = graph.requireNode(from);
        Node b = graph.requireNode(goal);
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return (int) Math.floor(Math.hypot(dx, dy) * scale);
    }
}
