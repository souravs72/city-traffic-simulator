package com.traffic.model.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

import org.junit.jupiter.api.Test;

class TrafficStateTest {

    @Test
    void respectsCapacity() {
        RoadGraph graph = new GraphBuilder()
                .addNode(new NodeId(0), "A")
                .addNode(new NodeId(1), "B")
                .addEdge(new EdgeId(0), new NodeId(0), new NodeId(1), 2, 1)
                .build();
        TrafficState traffic = new TrafficState(graph);
        EdgeId edge = new EdgeId(0);

        assertTrue(traffic.tryEnter(edge));
        assertEquals(1, traffic.occupancy(edge));
        assertFalse(traffic.tryEnter(edge));
        traffic.leave(edge);
        assertTrue(traffic.tryEnter(edge));
    }
}
