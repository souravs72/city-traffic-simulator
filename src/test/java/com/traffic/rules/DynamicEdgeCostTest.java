package com.traffic.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.traffic.TrafficState;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Routers;
import com.traffic.routing.RoutingAlgorithm;

import org.junit.jupiter.api.Test;

class DynamicEdgeCostTest {

    @Test
    void addsCongestionPenaltyAndAvoidsClosedEdge() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        NodeId c = new NodeId(2);
        EdgeId ab = new EdgeId(0);
        EdgeId ac = new EdgeId(1);

        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A", 0, 0)
                .addNode(b, "B", 5, 0)
                .addNode(c, "C", 0, 5)
                .addEdge(ab, a, b, 5, 2)
                .addEdge(ac, a, c, 6, 2)
                .build();

        TrafficState traffic = new TrafficState(graph);
        assertTrue(traffic.tryEnter(ab));

        EdgeCost cost = new DynamicEdgeCost(traffic, 10);
        assertEquals(15, cost.cost(graph.requireEdge(ab)));
        assertEquals(6, cost.cost(graph.requireEdge(ac)));

        traffic.close(ab);
        assertEquals(1_000_000, cost.cost(graph.requireEdge(ab)));

        Path path = Routers.create(RoutingAlgorithm.DIJKSTRA, graph)
                .findPath(graph, a, c, cost)
                .orElseThrow();
        assertEquals(1, path.hopCount());
        assertEquals(ac, path.edges().get(0));
    }
}
