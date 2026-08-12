package com.traffic.api.dto;

import java.util.List;

public record VehicleDto(
        int id,
        String name,
        int origin,
        int destination,
        int fuel,
        int fuelBurned,
        boolean arrived,
        int replanCount,
        String positionType,
        Integer nodeId,
        Integer edgeId,
        Integer ticksRemaining,
        int plannedShortestTicks,
        int plannedLiveTicks,
        int spawnedAtTick,
        Integer arrivedAtTick,
        Integer actualTicks,
        Integer remainingShortestEta,
        Integer remainingLiveEta,
        String serviceClass,
        int scheduledDepartAtTick,
        List<Integer> remainingEdgeIds
) {
}
