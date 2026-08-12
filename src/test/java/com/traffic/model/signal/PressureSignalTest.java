package com.traffic.model.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PressureSignalTest {

    @Test
    void doesNotBlockWhenOpposingApproachHasNoDemand() {
        TrafficLight ns = new TrafficLight("NS", Set.of(new EdgeId(1)), 3, 1, 3, LightColor.RED);
        TrafficLight ew = new TrafficLight("EW", Set.of(new EdgeId(2)), 3, 1, 3, LightColor.GREEN);

        // One-sided demand: skip yellow — both stay open (no real conflict).
        SignalNetwork.controlPair(ns, ew, 4, 0);
        assertEquals(LightColor.GREEN, ns.color());
        assertEquals(LightColor.GREEN, ew.color());
    }

    @Test
    void idleJunctionKeepsBothApproachesGreen() {
        TrafficLight a = new TrafficLight("A", Set.of(new EdgeId(1)), 2, 1, 2, LightColor.RED);
        TrafficLight b = new TrafficLight("B", Set.of(new EdgeId(2)), 2, 1, 2, LightColor.RED);
        SignalNetwork.controlPair(a, b, 0, 0);
        assertEquals(LightColor.GREEN, a.color());
        assertEquals(LightColor.GREEN, b.color());
    }

    @Test
    void dualDemandForcesExclusiveGreen() {
        TrafficLight a = new TrafficLight("A", Set.of(new EdgeId(1)), 2, 1, 2, LightColor.RED);
        TrafficLight b = new TrafficLight("B", Set.of(new EdgeId(2)), 2, 1, 2, LightColor.RED);
        SignalNetwork.controlPair(a, b, 5, 3);
        assertTrue(a.color() == LightColor.GREEN ^ b.color() == LightColor.GREEN);
        assertTrue(a.color() == LightColor.RED || b.color() == LightColor.RED);
    }
}
