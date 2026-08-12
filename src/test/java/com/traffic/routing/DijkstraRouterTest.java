package com.traffic.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DijkstraRouterTest {

    private RoadGraph graph;
    private final Router router = new DijkstraRouter();
    private final EdgeCost cost = EdgeCost.baseWeight();

    private final NodeId a = new NodeId(0);
    private final NodeId b = new NodeId(1);
    private final NodeId c = new NodeId(2);
    private final NodeId d = new NodeId(3);

    @BeforeEach
    void setUp() {
        // A --3--> B --4--> C
        // A -------10-----> C
        // D isolated
        graph = new GraphBuilder()
                .addNode(a, "A", 0, 0)
                .addNode(b, "B", 3, 0)
                .addNode(c, "C", 3, 4)
                .addNode(d, "D", 100, 100)
                .addEdge(new EdgeId(0), a, b, 3, 2)
                .addEdge(new EdgeId(1), b, c, 4, 2)
                .addEdge(new EdgeId(2), a, c, 10, 2)
                .build();
    }

    @Test
    void findsCheaperTwoHopPath() {
        Path path = router.findPath(graph, a, c, cost).orElseThrow();
        assertEquals(7, path.totalCost());
        assertEquals(List.of(new EdgeId(0), new EdgeId(1)), path.edges());
    }

    @Test
    void sameStartAndGoalIsEmptyPath() {
        Path path = router.findPath(graph, a, a, cost).orElseThrow();
        assertTrue(path.isEmpty());
        assertEquals(0, path.totalCost());
    }

    @Test
    void unreachableReturnsEmpty() {
        Optional<Path> path = router.findPath(graph, a, d, cost);
        assertTrue(path.isEmpty());
    }
}
