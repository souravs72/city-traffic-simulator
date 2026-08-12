package com.traffic.config;

/**
 * Knobs for generating a large playable city grid.
 * Example: 20×20 → 400 intersections, great for zoom+minimap UI later.
 */
public record CityGenConfig(
        int rows,
        int cols,
        double spacing,
        int defaultCapacity,
        boolean twoWayStreets
) {
    public CityGenConfig {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows and cols must be > 0");
        }
        if (spacing <= 0) {
            throw new IllegalArgumentException("spacing must be > 0");
        }
        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be > 0");
        }
    }

    /** Tiny sandbox (matches old demo scale). */
    public static CityGenConfig playground() {
        return new CityGenConfig(3, 3, 4.0, 2, true);
    }

    /** Medium city for local play. */
    public static CityGenConfig downtown() {
        return new CityGenConfig(10, 10, 3.0, 3, true);
    }

    /** Big graph for “wow” demos / stress tests. */
    public static CityGenConfig megacity() {
        return new CityGenConfig(25, 25, 2.5, 4, true);
    }

    public int expectedNodes() {
        return rows * cols;
    }
}
