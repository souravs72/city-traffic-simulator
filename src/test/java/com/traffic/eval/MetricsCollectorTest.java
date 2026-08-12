package com.traffic.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit tests for MetricsCollector. User: implement research-grade plan. */
class MetricsCollectorTest {

    @Test
    void percentile_knownVector() {
        List<Integer> sorted = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertEquals(1.0, MetricsCollector.percentile(sorted, 0), 1e-9);
        assertEquals(10.0, MetricsCollector.percentile(sorted, 100), 1e-9);
        assertEquals(5.5, MetricsCollector.percentile(sorted, 50), 1e-9);
        assertEquals(9.1, MetricsCollector.percentile(sorted, 90), 1e-9);
    }

    @Test
    void jainIndex_equalSharesIsOne() {
        assertEquals(1.0, MetricsCollector.jainIndex(List.of(5, 5, 5, 5)), 1e-9);
    }

    @Test
    void jainIndex_emptyIsOne() {
        assertEquals(1.0, MetricsCollector.jainIndex(List.of()), 1e-9);
    }

    @Test
    void jainIndex_unequalIsBelowOne() {
        double j = MetricsCollector.jainIndex(List.of(1, 100));
        assertTrue(j < 1.0 && j > 0);
    }
}
