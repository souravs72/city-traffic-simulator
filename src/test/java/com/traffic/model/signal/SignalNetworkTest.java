package com.traffic.model.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SignalNetworkTest {

    @Test
    void cyclesGreenYellowRedAndOnlyGreenAllowsEntry() {
        EdgeId edge = new EdgeId(0);
        TrafficLight light = new TrafficLight(
                "A",
                Set.of(edge),
                1,
                1,
                1,
                LightColor.GREEN
        );
        SignalNetwork network = new SignalNetwork(List.of(light));

        assertTrue(network.isOpen(edge));
        assertEquals(LightColor.GREEN, light.color());

        light.tick();
        assertEquals(LightColor.YELLOW, light.color());
        assertFalse(network.isOpen(edge));

        light.tick();
        assertEquals(LightColor.RED, light.color());
        assertFalse(network.isOpen(edge));

        light.tick();
        assertEquals(LightColor.GREEN, light.color());
        assertTrue(network.isOpen(edge));
    }

    @Test
    void redThenGreenAfterFullRedPhase() {
        EdgeId edge = new EdgeId(0);
        TrafficLight light = new TrafficLight("A", Set.of(edge), 2, 1, 2, LightColor.RED);
        SignalNetwork network = new SignalNetwork(List.of(light));

        assertFalse(network.isOpen(edge));
        light.tick();
        assertFalse(network.isOpen(edge));
        light.tick();
        assertEquals(LightColor.GREEN, light.color());
        assertTrue(network.isOpen(edge));
    }

    @Test
    void uncontrolledEdgeAlwaysOpen() {
        SignalNetwork network = SignalNetwork.none();
        assertTrue(network.isOpen(new EdgeId(99)));
    }
}
