package com.traffic.api.dto;

public record CreateSessionRequest(
        String preset,
        int rows,
        int cols,
        int fleetSize,
        long seed,
        int initialFuel,
        int maxTicks,
        boolean replaceSaved
) {
    /** Backward-compatible ctor used by tests — replaces save. */
    public CreateSessionRequest(
            String preset,
            int rows,
            int cols,
            int fleetSize,
            long seed,
            int initialFuel,
            int maxTicks
    ) {
        this(preset, rows, cols, fleetSize, seed, initialFuel, maxTicks, true);
    }
}
