package com.traffic.model.signal;

import com.traffic.model.graph.EdgeId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Collection of lights. Uncontrolled edges are always open. */
public final class SignalNetwork {

    private final List<TrafficLight> lights;
    private final Map<EdgeId, TrafficLight> byEdge;

    public SignalNetwork(List<TrafficLight> lights) {
        this.lights = List.copyOf(Objects.requireNonNull(lights, "lights"));
        Map<EdgeId, TrafficLight> index = new HashMap<>();
        for (TrafficLight light : this.lights) {
            for (EdgeId edgeId : light.controlledEdges()) {
                TrafficLight previous = index.put(edgeId, light);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Edge " + edgeId + " controlled by both "
                                    + previous.name() + " and " + light.name());
                }
            }
        }
        this.byEdge = Map.copyOf(index);
    }

    public static SignalNetwork none() {
        return new SignalNetwork(List.of());
    }

    public List<TrafficLight> lights() {
        return lights;
    }

    public boolean isOpen(EdgeId edgeId) {
        Objects.requireNonNull(edgeId, "edgeId");
        TrafficLight light = byEdge.get(edgeId);
        return light == null || light.allows(edgeId);
    }

    public void tick() {
        for (TrafficLight light : lights) {
            light.tick();
        }
    }
}
