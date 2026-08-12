package com.traffic.api.dto;

public record CreateSessionRequest(
        int rows,
        int cols,
        int fleetSize,
        long seed,
        int initialFuel,
        int maxTicks
) {
}
