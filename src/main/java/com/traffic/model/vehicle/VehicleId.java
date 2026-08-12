package com.traffic.model.vehicle;

/** Stable identity of a car. */
public record VehicleId(int value) {
    public VehicleId {
        if (value < 0) {
            throw new IllegalArgumentException("VehicleId must be >= 0, got " + value);
        }
    }
}
