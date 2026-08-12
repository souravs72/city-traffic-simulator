package com.traffic.api.dto;

public record BlueprintEdgeDto(
        int from,
        int to,
        int baseWeight,
        int capacity,
        String roadType
) {
}
