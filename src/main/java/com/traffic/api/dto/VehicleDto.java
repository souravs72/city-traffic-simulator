package com.traffic.api.dto;

public record VehicleDto(
        int id,
        int destination,
        int fuel,
        int fuelBurned,
        boolean arrived,
        int replanCount,
        String positionType,
        Integer nodeId,
        Integer edgeId,
        Integer ticksRemaining
) {
}
