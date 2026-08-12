package com.traffic.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AStarRouterTest {

    private RoadGraph graph;
    private Router aStar;
    private final Router dijkstra = new DijkstraRouter();
    private final EdgeCost cost = EdgeCost.baseWeight();

    private final NodeId a = new NodeId(0);
    private final NodeId b = new NodeId(1);
    private final NodeId c = new NodeId(2);

    @BeforeEach
    void setUp() {
        // Coordinates match edge lengths → Euclidean heuristic is admissible.
        graph = new GraphBuilder()
                .addNode(a, "A", 0, 0)
                .addNode(b, "B", 3, 0)
                .addNode(c, "C", 3, 4)
                .addEdge(new EdgeId(0), a, b, 3, 2)
                .addEdge(new EdgeId(1), b, c, 4, 2)
                .addEdge(new EdgeId(2), a, c, 10, 2)
                .build();
        aStar = new AStarRouter(new EuclideanHeuristic(graph));
    }

    @Test
    void matchesDijkstraCostAndEdges() {
        Path viaAStar = aStar.findPath(graph, a, c, cost).orElseThrow();
        Path viaDijkstra = dijkstra.findPath(graph, a, c, cost).orElseThrow();

        assertEquals(viaDijkstra.totalCost(), viaAStar.totalCost());
        assertEquals(viaDijkstra.edges(), viaAStar.edges());
        assertEquals(7, viaAStar.totalCost());
        assertEquals(List.of(new EdgeId(0), new EdgeId(1)), viaAStar.edges());
    }

    @Test
    void routersFactorySelectsAlgorithms() {
        Path d = Routers.create(RoutingAlgorithm.DIJKSTRA, graph)
                .findPath(graph, a, c, cost)
                .orElseThrow();
        Path s = Routers.create(RoutingAlgorithm.ASTAR, graph)
                .findPath(graph, a, c, cost)
                .orElseThrow();
        assertEquals(d.totalCost(), s.totalCost());
    }
}
