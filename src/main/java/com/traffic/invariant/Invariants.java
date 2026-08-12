package com.traffic.invariant;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Correctness checks: capacity and fuel ledger. Detect only — do not fix. */
public final class Invariants {

    private Invariants() {
    }

    public static void checkCapacity(TrafficState traffic) {
        Objects.requireNonNull(traffic, "traffic");
        for (Edge edge : traffic.graph().edges()) {
            int occ = traffic.occupancy(edge.id());
            if (occ < 0) {
                throw new IllegalStateException("Negative occupancy on " + edge.id());
            }
            if (occ > edge.capacity()) {
                throw new IllegalStateException(
                        "Capacity exceeded on " + edge.id() + ": " + occ + " > " + edge.capacity());
            }
        }
    }

    /**
     * Each vehicle's fuel + burned must equal the recorded initial ledger snapshot.
     * Pass the expected ledger (usually initialFuel at spawn).
     */
    public static void checkFuel(Vehicle vehicle, int expectedLedger) {
        Objects.requireNonNull(vehicle, "vehicle");
        if (vehicle.fuelLedger() != expectedLedger) {
            throw new IllegalStateException(
                    "Fuel not conserved for " + vehicle.id()
                            + ": ledger=" + vehicle.fuelLedger()
                            + " expected=" + expectedLedger);
        }
        if (vehicle.fuel() < 0 || vehicle.fuelBurned() < 0) {
            throw new IllegalStateException("Negative fuel fields for " + vehicle.id());
        }
    }

    public static void checkAll(TrafficState traffic, List<Vehicle> vehicles, int expectedFuelLedger) {
        checkCapacity(traffic);
        List<String> errors = new ArrayList<>();
        for (Vehicle v : vehicles) {
            try {
                checkFuel(v, expectedFuelLedger);
            } catch (IllegalStateException e) {
                errors.add(e.getMessage());
            }
        }
        for (Vehicle v : vehicles) {
            if (v.position() instanceof VehiclePosition.OnEdge onEdge) {
                EdgeId id = onEdge.edge();
                if (traffic.occupancy(id) <= 0) {
                    errors.add("Vehicle " + v.id() + " on " + id + " but occupancy is 0");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join("; ", errors));
        }
    }
}
