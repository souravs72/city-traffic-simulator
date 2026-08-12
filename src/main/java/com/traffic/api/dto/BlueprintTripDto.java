package com.traffic.api.dto;

public record BlueprintTripDto(int from, int to, String name, String serviceClass, int scheduledDepartAtTick) {
    public BlueprintTripDto(int from, int to, String name) {
        this(from, to, name, "CIVILIAN", 0);
    }
}
