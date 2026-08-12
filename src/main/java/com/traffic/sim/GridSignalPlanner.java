package com.traffic.sim;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Places NS/EW traffic lights at multi-outgoing intersections.
 * Red duration equals green+yellow so opposite phases never both show green.
 * Paired lights enable adaptive green extension for the busier approach.
 */
public final class GridSignalPlanner {

    private GridSignalPlanner() {
    }

    public static SignalNetwork forGraph(RoadGraph graph, LightTiming timing) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(timing, "timing");
        LightTiming synced = syncedTiming(timing);
        List<TrafficLight> lights = new ArrayList<>();
        List<SignalNetwork.Pair> pairs = new ArrayList<>();

        for (Node node : graph.nodes()) {
            List<Edge> outgoing = graph.outgoing(node.id());
            if (outgoing.size() < 2) {
                continue;
            }

            Set<EdgeId> northSouth = new HashSet<>();
            Set<EdgeId> eastWest = new HashSet<>();
            for (Edge edge : outgoing) {
                if (isPrimarilyVertical(graph, edge)) {
                    northSouth.add(edge.id());
                } else {
                    eastWest.add(edge.id());
                }
            }

            // FlowGuard starts all-clear; red appears only under real conflict / VIP cut.
            if (!northSouth.isEmpty() && !eastWest.isEmpty()) {
                TrafficLight ns = new TrafficLight(
                        lightName(node.id(), "NS"),
                        northSouth,
                        synced,
                        LightColor.GREEN
                );
                TrafficLight ew = new TrafficLight(
                        lightName(node.id(), "EW"),
                        eastWest,
                        synced,
                        LightColor.GREEN
                );
                lights.add(ns);
                lights.add(ew);
                pairs.add(new SignalNetwork.Pair(ns, ew));
            } else {
                Set<EdgeId> all = new HashSet<>();
                all.addAll(northSouth);
                all.addAll(eastWest);
                lights.add(new TrafficLight(
                        lightName(node.id(), "ALL"),
                        all,
                        synced,
                        LightColor.GREEN
                ));
            }
        }

        return new SignalNetwork(lights, pairs);
    }

    /** Make red phase cover the other approach's green+yellow so they stay exclusive. */
    static LightTiming syncedTiming(LightTiming timing) {
        int red = timing.greenTicks() + timing.yellowTicks();
        return new LightTiming(timing.greenTicks(), timing.yellowTicks(), red);
    }

    private static boolean isPrimarilyVertical(RoadGraph graph, Edge edge) {
        Node from = graph.requireNode(edge.from());
        Node to = graph.requireNode(edge.to());
        double dx = Math.abs(to.x() - from.x());
        double dy = Math.abs(to.y() - from.y());
        return dy >= dx;
    }

    private static String lightName(NodeId nodeId, String axis) {
        return "N" + nodeId.value() + "-" + axis;
    }
}
