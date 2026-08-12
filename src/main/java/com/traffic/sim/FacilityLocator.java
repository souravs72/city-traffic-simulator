package com.traffic.sim;

import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Spatial nearest-facility lookup via coarse grid buckets. */
public final class FacilityLocator {

    private static final double CELL = 120.0;

    private FacilityLocator() {
    }

    public static Node nearest(EditableCity city, RoadGraph graph, FacilityKind kind, NodeId near) {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(kind, "kind");
        Node target = graph.requireNode(near);
        Map<Long, List<Node>> buckets = new HashMap<>();
        for (Node n : city.nodes()) {
            if (n.facility() != kind) {
                continue;
            }
            buckets.computeIfAbsent(cellKey(n.x(), n.y()), k -> new ArrayList<>()).add(n);
        }
        if (buckets.isEmpty()) {
            return null;
        }
        int cx = (int) Math.floor(target.x() / CELL);
        int cy = (int) Math.floor(target.y() / CELL);
        Node best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int r = 0; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (r > 0 && Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    List<Node> cell = buckets.get((((long) (cx + dx)) << 32) | ((cy + dy) & 0xffff_ffffL));
                    if (cell == null) {
                        continue;
                    }
                    for (Node n : cell) {
                        double d = Math.hypot(n.x() - target.x(), n.y() - target.y());
                        if (d < bestD) {
                            bestD = d;
                            best = n;
                        }
                    }
                }
            }
            if (best != null && bestD <= (r + 1) * CELL * 1.5) {
                break;
            }
        }
        return best;
    }

    private static long cellKey(double x, double y) {
        int cx = (int) Math.floor(x / CELL);
        int cy = (int) Math.floor(y / CELL);
        return (((long) cx) << 32) | (cy & 0xffff_ffffL);
    }
}
