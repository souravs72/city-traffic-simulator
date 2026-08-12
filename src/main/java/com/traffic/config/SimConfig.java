package com.traffic.config;

import com.traffic.routing.RoutingAlgorithm;

import java.util.Objects;

/** Simulation knobs (sizes, fuel, which router). */
public record SimConfig(
        int maxTicks,
        int initialFuel,
        RoutingAlgorithm routingAlgorithm
) {
    public SimConfig {
        Objects.requireNonNull(routingAlgorithm, "routingAlgorithm");
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks must be > 0");
        }
        if (initialFuel < 0) {
            throw new IllegalArgumentException("initialFuel must be >= 0");
        }
    }

    public static SimConfig defaults() {
        return new SimConfig(100, 50, RoutingAlgorithm.DIJKSTRA);
    }
}
