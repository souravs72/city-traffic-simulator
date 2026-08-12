package com.traffic.model.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphBuilderTest {

    @Test
    void buildsTinyCity() {
        RoadGraph g = new GraphBuilder()
                .addNode(new NodeId(0), "A")
                .addNode(new NodeId(1), "B")
                .addEdge(new EdgeId(0), new NodeId(0), new NodeId(1), 3, 2)
                .build();

        assertEquals(2, g.nodeCount());
        assertEquals(1, g.edgeCount());

        var out = g.outgoing(new NodeId(0));
        assertEquals(1, out.size());
        assertEquals(new NodeId(1), out.get(0).to());
        assertEquals(3, out.get(0).baseWeight());
        assertEquals(2, out.get(0).capacity());

        assertTrue(g.outgoing(new NodeId(1)).isEmpty());
    }

    @Test
    void rejectsDuplicateNode() {
        GraphBuilder b = new GraphBuilder().addNode(new NodeId(0), "A");
        assertThrows(IllegalArgumentException.class,
                () -> b.addNode(new NodeId(0), "A2"));
    }

    @Test
    void rejectsEdgeBeforeNodesExist() {
        GraphBuilder b = new GraphBuilder().addNode(new NodeId(0), "A");
        assertThrows(IllegalArgumentException.class,
                () -> b.addEdge(new EdgeId(0), new NodeId(0), new NodeId(1), 3, 2));
    }
}