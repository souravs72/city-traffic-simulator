package com.traffic.api.dto;

public record EdgeDto(
        int id,
        int from,
        int to,
        int baseWeight,
        int capacity,
        int occupancy,
        String roadType,
        String lightColor
) {
}
