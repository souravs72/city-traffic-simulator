package com.traffic.config;

import com.traffic.model.signal.LightTiming;
import com.traffic.routing.RoutingAlgorithm;

import java.util.Objects;

/**
 * Playable knobs — tweak these (later from UI) without touching sim logic.
 */
public record SimConfig(
        int maxTicks,
        int initialFuel,
        RoutingAlgorithm routingAlgorithm,
        LightTiming lightTiming,
        int congestionPenaltyPerCar,
        int accidentDurationTicks,
        boolean verboseTickLog,
        int parallelRoutingThreshold
) {
    public SimConfig {
        Objects.requireNonNull(routingAlgorithm, "routingAlgorithm");
        Objects.requireNonNull(lightTiming, "lightTiming");
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks must be > 0");
        }
        if (initialFuel < 0) {
            throw new IllegalArgumentException("initialFuel must be >= 0");
        }
        if (congestionPenaltyPerCar < 0) {
            throw new IllegalArgumentException("congestionPenaltyPerCar must be >= 0");
        }
        if (accidentDurationTicks <= 0) {
            throw new IllegalArgumentException("accidentDurationTicks must be > 0");
        }
        if (parallelRoutingThreshold < 0) {
            throw new IllegalArgumentException("parallelRoutingThreshold must be >= 0");
        }
    }

    /**
     * @param parallelRoutingThreshold fleet/trip size at which pathfinding uses the common pool;
     *                                  {@code 0} means always parallel when there is work.
     */
    public static SimConfig defaults() {
        return new SimConfig(
                100,
                50,
                RoutingAlgorithm.DIJKSTRA,
                LightTiming.playful(),
                2,
                8,
                true,
                8
        );
    }

    public boolean useParallelRouting(int workItems) {
        return workItems >= parallelRoutingThreshold;
    }

    public SimConfig withAlgorithm(RoutingAlgorithm algorithm) {
        return new SimConfig(
                maxTicks,
                initialFuel,
                algorithm,
                lightTiming,
                congestionPenaltyPerCar,
                accidentDurationTicks,
                verboseTickLog,
                parallelRoutingThreshold
        );
    }

    public SimConfig withLightTiming(LightTiming timing) {
        return new SimConfig(
                maxTicks,
                initialFuel,
                routingAlgorithm,
                timing,
                congestionPenaltyPerCar,
                accidentDurationTicks,
                verboseTickLog,
                parallelRoutingThreshold
        );
    }

    public SimConfig withParallelRoutingThreshold(int threshold) {
        return new SimConfig(
                maxTicks,
                initialFuel,
                routingAlgorithm,
                lightTiming,
                congestionPenaltyPerCar,
                accidentDurationTicks,
                verboseTickLog,
                threshold
        );
    }
}
