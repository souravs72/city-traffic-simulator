package com.traffic.model.graph;

import java.util.Locale;

/**
 * Real-world-ish street classes.
 * {@code travelTicks} = seconds per block on the play clock; {@code capacity} = cars on the segment.
 */
public enum RoadType {
    /** Fast arterials — high throughput. */
    HIGHWAY(1, 8),
    /** Normal city streets (default). */
    AVENUE(1, 3),
    /** Narrow / slow local roads. */
    ALLEY(2, 1);

    private final int travelTicks;
    private final int capacity;

    RoadType(int travelTicks, int capacity) {
        this.travelTicks = travelTicks;
        this.capacity = capacity;
    }

    public int travelTicks() {
        return travelTicks;
    }

    public int capacity() {
        return capacity;
    }

    public static RoadType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AVENUE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AVENUE;
        }
    }

    /** Best-effort classify from stored edge knobs (for snapshots / older maps). */
    public static RoadType classify(int baseWeight, int capacity) {
        if (capacity >= 6 && baseWeight <= 1) {
            return HIGHWAY;
        }
        if (baseWeight >= 2 || capacity <= 1) {
            return ALLEY;
        }
        return AVENUE;
    }
}
