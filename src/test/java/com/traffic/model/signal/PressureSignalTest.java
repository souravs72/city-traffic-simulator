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

        // Only NS has demand — EW should clear and NS should get green without staying red forever.
        SignalNetwork.controlPair(ns, ew, 4, 0);
        assertEquals(LightColor.YELLOW, ew.color());

        SignalNetwork.controlPair(ns, ew, 4, 0);
        assertTrue(ns.color() == LightColor.GREEN || ew.color() == LightColor.YELLOW);
        // Finish yellow
        while (ew.color() == LightColor.YELLOW) {
            SignalNetwork.controlPair(ns, ew, 4, 0);
        }
        assertEquals(LightColor.GREEN, ns.color());
        assertEquals(LightColor.RED, ew.color());
    }

    @Test
    void idleJunctionKeepsOneApproachGreen() {
        TrafficLight a = new TrafficLight("A", Set.of(new EdgeId(1)), 2, 1, 2, LightColor.RED);
        TrafficLight b = new TrafficLight("B", Set.of(new EdgeId(2)), 2, 1, 2, LightColor.RED);
        SignalNetwork.controlPair(a, b, 0, 0);
        assertTrue(a.color() == LightColor.GREEN || b.color() == LightColor.GREEN);
        assertTrue(!(a.color() == LightColor.GREEN && b.color() == LightColor.GREEN));
    }
}
