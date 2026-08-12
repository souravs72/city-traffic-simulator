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

    @Test
    void tryEnterRespectsCapacityUnderContention() throws Exception {
        RoadGraph graph = new GraphBuilder()
                .addNode(new NodeId(0), "A")
                .addNode(new NodeId(1), "B")
                .addEdge(new EdgeId(0), new NodeId(0), new NodeId(1), 2, 2)
                .build();
        TrafficState traffic = new TrafficState(graph);
        EdgeId edge = new EdgeId(0);

        int threads = 32;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (traffic.tryEnter(edge)) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(2, successes.get());
        assertEquals(2, traffic.occupancy(edge));
    }
}
