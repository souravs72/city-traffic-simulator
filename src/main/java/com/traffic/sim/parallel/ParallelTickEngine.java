package com.traffic.sim.parallel;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.priority.PriorityMechanisms;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Parallel tick phases. Callers: Simulation.step.
 */
public final class ParallelTickEngine {

    private ParallelTickEngine() {
    }

    public static void advanceOnEdges(
            SimExecutor executor,
            List<Vehicle> vehicles,
            TrafficState traffic,
            int arrivalTick
    ) {
        Objects.requireNonNull(executor, "executor");
        List<Vehicle> movers = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.arrived() && vehicle.position() instanceof VehiclePosition.OnEdge) {
                movers.add(vehicle);
            }
        }
        if (movers.isEmpty()) {
            return;
        }
        if (movers.size() < 4) {
            for (Vehicle vehicle : movers) {
                advanceOne(vehicle, traffic, arrivalTick);
            }
            return;
        }
        executor.runTick(() -> movers.parallelStream().forEach(v -> advanceOne(v, traffic, arrivalTick)));
    }

    private static void advanceOne(Vehicle vehicle, TrafficState traffic, int arrivalTick) {
        if (!(vehicle.position() instanceof VehiclePosition.OnEdge onEdge)) {
            return;
        }
        boolean finished = vehicle.advanceOnEdge();
        if (finished) {
            EdgeId edgeId = onEdge.edge();
            Edge edge = traffic.graph().requireEdge(edgeId);
            traffic.leave(edgeId);
            vehicle.finishEdgeAt(edge.to());
            if (vehicle.arrived()) {
                vehicle.noteArrival(arrivalTick);
            }
        }
    }

    public static void departByStripe(
            SimExecutor executor,
            List<Vehicle> atNodes,
            TrafficState traffic,
            CorridorBoard corridors,
            PriorityMechanisms mechanisms,
            int tick,
            Map<VehicleId, Integer> waitTicksByVehicle,
            Map<EdgeId, Integer> highestWaitingRank,
            BiPredicate<Vehicle, EdgeId> signalAllows
    ) {
        Objects.requireNonNull(executor, "executor");
        PriorityMechanisms mech = Objects.requireNonNullElse(mechanisms, PriorityMechanisms.none());
        List<Vehicle> candidates = new ArrayList<>();
        for (Vehicle vehicle : atNodes) {
            if (!vehicle.mayDepartAt(tick) || !vehicle.hasRemainingEdges()) {
                continue;
            }
            candidates.add(vehicle);
        }
        if (candidates.isEmpty()) {
            return;
        }

        Map<Integer, List<Vehicle>> byStripe = new HashMap<>();
        for (Vehicle vehicle : candidates) {
            EdgeId next = vehicle.peekNextEdge().orElseThrow();
            int stripe = traffic.stripeIndex(next);
            byStripe.computeIfAbsent(stripe, k -> new ArrayList<>()).add(vehicle);
        }

        Comparator<Vehicle> order = Comparator
                .comparingInt((Vehicle v) -> mech.priorityDeparture() ? v.serviceClass().rank() : 0)
                .reversed()
                .thenComparingInt(v -> -waitTicksByVehicle.getOrDefault(v.id(), 0))
                .thenComparingInt(v -> v.id().value());

        Runnable work = () -> byStripe.values().parallelStream().forEach(group -> {
            List<Vehicle> sorted = new ArrayList<>(group);
            sorted.sort(order);
            for (Vehicle vehicle : sorted) {
                EdgeId next = vehicle.peekNextEdge().orElseThrow();
                if (mech.corridorBlocking() && corridors.blocks(next, vehicle.serviceClass())) {
                    continue;
                }
                if (mech.priorityDeparture()
                        && vehicle.serviceClass().rank() < highestWaitingRank.getOrDefault(next, 0)) {
                    continue;
                }
                if (signalAllows.test(vehicle, next) && traffic.tryEnter(next)) {
                    Edge edge = traffic.graph().requireEdge(next);
                    vehicle.enterEdge(next, edge.baseWeight());
                    waitTicksByVehicle.remove(vehicle.id());
                }
            }
        });

        if (byStripe.size() == 1) {
            work.run();
        } else {
            executor.runTick(work);
        }
    }

    public static Map<EdgeId, Integer> highestWaitingRanks(List<Vehicle> atNodes, int tick) {
        Map<EdgeId, Integer> ranks = new ConcurrentHashMap<>();
        for (Vehicle vehicle : atNodes) {
            if (!vehicle.mayDepartAt(tick) || !vehicle.hasRemainingEdges()) {
                continue;
            }
            vehicle.peekNextEdge().ifPresent(edgeId ->
                    ranks.merge(edgeId, vehicle.serviceClass().rank(), Math::max));
        }
        return ranks;
    }
}
