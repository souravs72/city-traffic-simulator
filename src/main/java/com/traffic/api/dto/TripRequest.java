package com.traffic.api.dto;

public record TripRequest(int from, int to, String name, String serviceClass, int scheduledDepartAtTick) {
    public TripRequest(int from, int to, String name) {
        this(from, to, name, "CIVILIAN", 0);
    }
}
