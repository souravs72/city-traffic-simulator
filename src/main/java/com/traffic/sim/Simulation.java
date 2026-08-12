package com.traffic.sim;

import com.traffic.invariant.Invariants;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.rules.Replanner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-threaded tick loop for a fleet of cars.
 * Phase A: advance cars on edges.
 * Phase B: replan if next road has an ✕ accident, then try to depart (light + capacity).
 * Phase C: advance lights + accident timers.
 */
public final class Simulation {

    private final TrafficState traffic;
    private final SignalNetwork signals;
    private final List<Vehicle> vehicles;
    private final Optional<Replanner> replanner;
    private final int expectedFuelLedger;
    private final boolean checkInvariants;
    private int tick;
    private int totalReplans;

    public Simulation(TrafficState traffic, List<Vehicle> vehicles, int expectedFuelLedger) {
        this(traffic, SignalNetwork.none(), vehicles, expectedFuelLedger, null, true);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger
    ) {
        this(traffic, signals, vehicles, expectedFuelLedger, null, true);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            Replanner replanner
    ) {
        this(traffic, signals, vehicles, expectedFuelLedger, replanner, true);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            Replanner replanner,
            boolean checkInvariants
    ) {
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        this.signals = Objects.requireNonNull(signals, "signals");
        this.vehicles = List.copyOf(Objects.requireNonNull(vehicles, "vehicles"));
        this.replanner = Optional.ofNullable(replanner);
        this.expectedFuelLedger = expectedFuelLedger;
        this.checkInvariants = checkInvariants;
        this.tick = 0;
        this.totalReplans = 0;
        if (checkInvariants) {
            Invariants.checkAll(traffic, this.vehicles, expectedFuelLedger);
        }
    }

    public int tick() {
        return tick;
    }

    public int totalReplans() {
        return totalReplans;
    }

    public List<Vehicle> vehicles() {
        return vehicles;
    }

    public TrafficState traffic() {
        return traffic;
    }

    public SignalNetwork signals() {
        return signals;
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

        // Phase B — replan if blocked, then departures
        for (Vehicle vehicle : vehicles) {
            if (vehicle.arrived()) {
                continue;
            }
            if (!(vehicle.position() instanceof VehiclePosition.AtNode)) {
                continue;
            }

            maybeReplanIfNextBlocked(vehicle);

            if (vehicle.hasRemainingEdges()) {
                EdgeId next = vehicle.peekNextEdge().orElseThrow();
                if (signals.isOpen(next) && traffic.tryEnter(next)) {
                    Edge edge = traffic.graph().requireEdge(next);
                    vehicle.enterEdge(next, edge.baseWeight());
                }
            }
        }

        // Phase C — advance lights + accident timers
        signals.tick();
        traffic.tickAccidents();

        tick++;
        if (checkInvariants) {
            Invariants.checkAll(traffic, vehicles, expectedFuelLedger);
        }
    }

    private void maybeReplanIfNextBlocked(Vehicle vehicle) {
        if (replanner.isEmpty()) {
            return;
        }
        boolean needsReplan = !vehicle.hasRemainingEdges();
        if (vehicle.hasRemainingEdges()) {
            EdgeId next = vehicle.peekNextEdge().orElseThrow();
            needsReplan = traffic.isClosed(next);
        }
        if (!needsReplan) {
            return;
        }
        int before = vehicle.replanCount();
        if (replanner.get().replan(vehicle, traffic.graph()) && vehicle.replanCount() > before) {
            totalReplans++;
        }
    }

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
