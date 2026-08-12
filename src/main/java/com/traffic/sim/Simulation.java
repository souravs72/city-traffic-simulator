package com.traffic.sim;

import com.traffic.invariant.Invariants;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;

import java.util.List;
import java.util.Objects;

/**
 * Single-threaded tick loop.
 * Phase A: advance cars already on edges.
 * Phase B: cars at nodes try to enter their next edge (capacity-limited).
 */
public final class Simulation {

    private final TrafficState traffic;
    private final List<Vehicle> vehicles;
    private final int expectedFuelLedger;
    private final boolean checkInvariants;
    private int tick;

    public Simulation(TrafficState traffic, List<Vehicle> vehicles, int expectedFuelLedger) {
        this(traffic, vehicles, expectedFuelLedger, true);
    }

    public Simulation(
            TrafficState traffic,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            boolean checkInvariants
    ) {
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        this.vehicles = List.copyOf(Objects.requireNonNull(vehicles, "vehicles"));
        this.expectedFuelLedger = expectedFuelLedger;
        this.checkInvariants = checkInvariants;
        this.tick = 0;
        if (checkInvariants) {
            Invariants.checkAll(traffic, this.vehicles, expectedFuelLedger);
        }
    }

    public int tick() {
        return tick;
    }

    public List<Vehicle> vehicles() {
        return vehicles;
    }

    public TrafficState traffic() {
        return traffic;
    }

    public boolean allArrived() {
        for (Vehicle v : vehicles) {
            if (!v.arrived()) {
                return false;
            }
        }
        return true;
    }

    public void step() {
        // Phase A — finish / continue travel
        for (Vehicle vehicle : vehicles) {
            if (vehicle.arrived()) {
                continue;
            }
            if (vehicle.position() instanceof VehiclePosition.OnEdge onEdge) {
                boolean finished = vehicle.advanceOnEdge();
                if (finished) {
                    EdgeId edgeId = onEdge.edge();
                    Edge edge = traffic.graph().requireEdge(edgeId);
                    traffic.leave(edgeId);
                    vehicle.finishEdgeAt(edge.to());
                }
            }
        }

        // Phase B — departures from nodes
        for (Vehicle vehicle : vehicles) {
            if (vehicle.arrived()) {
                continue;
            }
            if (vehicle.position() instanceof VehiclePosition.AtNode
                    && vehicle.hasRemainingEdges()) {
                EdgeId next = vehicle.peekNextEdge().orElseThrow();
                if (traffic.tryEnter(next)) {
                    Edge edge = traffic.graph().requireEdge(next);
                    vehicle.enterEdge(next, edge.baseWeight());
                }
            }
        }

        tick++;
        if (checkInvariants) {
            Invariants.checkAll(traffic, vehicles, expectedFuelLedger);
        }
    }

    /** Run until all arrived or {@code maxTicks} reached. Returns ticks executed. */
    public int run(int maxTicks) {
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks must be > 0");
        }
        int started = tick;
        while (tick - started < maxTicks && !allArrived()) {
            step();
        }
        return tick - started;
    }

    public long arrivedCount() {
        return vehicles.stream().filter(Vehicle::arrived).count();
    }
}
