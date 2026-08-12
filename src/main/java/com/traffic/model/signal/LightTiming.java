package com.traffic.model.signal;

/**
 * How long each light color lasts (in simulation ticks).
 * Tunable for play — shorter yellow = snappier city, longer = calmer.
 */
public record LightTiming(int greenTicks, int yellowTicks, int redTicks) {

    public LightTiming {
        if (greenTicks <= 0 || yellowTicks <= 0 || redTicks <= 0) {
            throw new IllegalArgumentException("All light phases must be > 0 ticks");
        }
    }

    /** Balanced city timing. */
    public static LightTiming defaults() {
        return new LightTiming(5, 2, 5);
    }

    /** Snappier lights for a playful demo. */
    public static LightTiming playful() {
        return new LightTiming(4, 2, 3);
    }

    public int duration(LightColor color) {
        return switch (color) {
            case GREEN -> greenTicks;
            case YELLOW -> yellowTicks;
            case RED -> redTicks;
        };
    }
}
