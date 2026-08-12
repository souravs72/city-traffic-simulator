package com.traffic.config;

import java.util.Locale;
import java.util.Optional;

/** Named starting canvases for the UI. */
public enum CityPreset {
    /** Empty canvas — draw your own nodes and roads (React-Flow style). */
    BLANK,
    PLAYGROUND,
    DOWNTOWN,
    MEGACITY;

    public boolean isBlank() {
        return this == BLANK;
    }

    public CityGenConfig toGenConfig() {
        return switch (this) {
            case BLANK -> throw new IllegalStateException("BLANK has no grid config");
            case PLAYGROUND -> CityGenConfig.playground();
            case DOWNTOWN -> CityGenConfig.downtown();
            case MEGACITY -> CityGenConfig.megacity();
        };
    }

    /** Default starter fleet. {@code 0} = players place their own trips. */
    public int defaultFleetSize() {
        return 0;
    }

    public int defaultFuel() {
        return switch (this) {
            case BLANK, PLAYGROUND -> 80;
            case DOWNTOWN -> 200;
            case MEGACITY -> 400;
        };
    }

    public int parallelRoutingThreshold() {
        return switch (this) {
            case BLANK, PLAYGROUND -> 4;
            case DOWNTOWN -> 8;
            case MEGACITY -> 16;
        };
    }

    public int defaultMaxTicks() {
        return switch (this) {
            case BLANK, PLAYGROUND -> 200;
            case DOWNTOWN -> 500;
            case MEGACITY -> 2000;
        };
    }

    public static Optional<CityPreset> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(BLANK);
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
