package com.traffic.sim;

import com.traffic.invariant.Invariants;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.Path;
import com.traffic.rules.Replanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tick loop for a fleet of cars.
 * CityFlow policy: priority departures, emergency signal preemption, corridor diversion.
 * Maps-like policy: congestion routing only — no priority privileges.
 */
public final class Simulation {

    private final TrafficState traffic;
    private final SignalNetwork signals;
    private final List<Vehicle> vehicles;
    private final Optional<Replanner> replanner;
    private final CorridorBoard corridors;
    private final ControlPolicy policy;
    private final int expectedFuelLedger;
    private final boolean checkInvariants;
    private final int parallelRoutingThreshold;
    private int tick;
    private final AtomicInteger totalReplans = new AtomicInteger();
    private final Map<VehicleId, Integer> waitTicksByVehicle = new HashMap<>();

    public Simulation(TrafficState traffic, List<Vehicle> vehicles, int expectedFuelLedger) {
        this(traffic, SignalNetwork.none(), vehicles, expectedFuelLedger, null, true, 8,
                new CorridorBoard(), ControlPolicy.CITY_FLOW);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger
    ) {
        this(traffic, signals, vehicles, expectedFuelLedger, null, true, 8,
                new CorridorBoard(), ControlPolicy.CITY_FLOW);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            Replanner replanner
    ) {
        this(traffic, signals, vehicles, expectedFuelLedger, replanner, true, 8,
                new CorridorBoard(), ControlPolicy.CITY_FLOW);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            Replanner replanner,
            boolean checkInvariants
    ) {
        this(traffic, signals, vehicles, expectedFuelLedger, replanner, checkInvariants, 8,
                new CorridorBoard(), ControlPolicy.CITY_FLOW);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            Replanner replanner,
            boolean checkInvariants,
            int parallelRoutingThreshold
    ) {
        this(traffic, signals, vehicles, expectedFuelLedger, replanner, checkInvariants,
                parallelRoutingThreshold, new CorridorBoard(), ControlPolicy.CITY_FLOW);
    }

    public Simulation(
            TrafficState traffic,
            SignalNetwork signals,
            List<Vehicle> vehicles,
            int expectedFuelLedger,
            Replanner replanner,
            boolean checkInvariants,
            int parallelRoutingThreshold,
            CorridorBoard corridors,
            ControlPolicy policy
    ) {
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        this.signals = Objects.requireNonNull(signals, "signals");
        this.vehicles = List.copyOf(Objects.requireNonNull(vehicles, "vehicles"));
        this.replanner = Optional.ofNullable(replanner);
        this.corridors = corridors == null ? new CorridorBoard() : corridors;
        this.policy = policy == null ? ControlPolicy.CITY_FLOW : policy;
        this.expectedFuelLedger = expectedFuelLedger;
        this.checkInvariants = checkInvariants;
        if (parallelRoutingThreshold < 0) {
            throw new IllegalArgumentException("parallelRoutingThreshold must be >= 0");
        }
        this.parallelRoutingThreshold = parallelRoutingThreshold;
        this.tick = 0;
        if (checkInvariants) {
            Invariants.checkAll(traffic, this.vehicles, expectedFuelLedger);
        }
    }

    public int tick() {
        return tick;
    }

    public int totalReplans() {
        return totalReplans.get();
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

    public CorridorBoard corridors() {
        return corridors;
    }

    public ControlPolicy policy() {
        return policy;
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
        corridors.setCurrentTick(tick);

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
                    if (vehicle.arrived()) {
                        vehicle.noteArrival(tick + 1);
                    }
                }
            }
        }

        // Phase B — queues, lights (with optional preemption), replan, priority departures
        List<Vehicle> atNodes = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.arrived() && vehicle.position() instanceof VehiclePosition.AtNode) {
                atNodes.add(vehicle);
            }
        }
        ageWaiting(atNodes);

        Map<EdgeId, Integer> waiting = waitingForSignals();
        Map<EdgeId, Integer> ages = waitAgeByEdge();
        Map<EdgeId, Integer> priority = policy.honorPriority() ? priorityByEdge() : Map.of();
        signals.tick(traffic, waiting, ages, priority);

        replanBlocked(atNodes);

        List<Vehicle> departOrder = new ArrayList<>(atNodes);
        if (policy.honorPriority()) {
            departOrder.sort(Comparator
                    .comparingInt((Vehicle v) -> v.serviceClass().rank()).reversed()
                    .thenComparingInt(v -> -waitTicksByVehicle.getOrDefault(v.id(), 0)));
        }

        for (Vehicle vehicle : departOrder) {
            if (!vehicle.mayDepartAt(tick)) {
                continue;
            }
            if (!vehicle.hasRemainingEdges()) {
                continue;
            }
            EdgeId next = vehicle.peekNextEdge().orElseThrow();
            if (policy.honorPriority()
                    && corridors.blocks(next, vehicle.serviceClass())) {
                continue;
            }
            if (signalAllows(vehicle, next) && traffic.tryEnter(next)) {
                Edge edge = traffic.graph().requireEdge(next);
                vehicle.enterEdge(next, edge.baseWeight());
                waitTicksByVehicle.remove(vehicle.id());
            }
        }

        // Phase C — accident timers
        traffic.tickAccidents();

        tick++;
        if (checkInvariants) {
            Invariants.checkAll(traffic, vehicles, expectedFuelLedger);
        }
    }

    private void replanBlocked(List<Vehicle> atNodes) {
        if (replanner.isEmpty() || atNodes.isEmpty()) {
            return;
        }
        Replanner planner = replanner.get();
        List<Vehicle> needReplan = new ArrayList<>();
        for (Vehicle vehicle : atNodes) {
            if (needsReplan(vehicle)) {
                needReplan.add(vehicle);
            }
        }
        if (needReplan.isEmpty()) {
            return;
        }

        if (needReplan.size() >= parallelRoutingThreshold) {
            ConcurrentHashMap<VehicleId, Optional<Path>> computed = new ConcurrentHashMap<>();
            needReplan.parallelStream().forEach(vehicle ->
                    computed.put(vehicle.id(), planner.computePath(vehicle, traffic.graph())));
            for (Vehicle vehicle : needReplan) {
                Optional<Path> path = computed.getOrDefault(vehicle.id(), Optional.empty());
                if (path.isPresent()) {
                    int before = vehicle.replanCount();
                    vehicle.replaceRemainingPath(path.get());
                    if (vehicle.replanCount() > before) {
                        totalReplans.incrementAndGet();
                    }
                }
            }
            return;
        }

        for (Vehicle vehicle : needReplan) {
            int before = vehicle.replanCount();
            if (planner.replan(vehicle, traffic.graph()) && vehicle.replanCount() > before) {
                totalReplans.incrementAndGet();
            }
        }
    }

    private void ageWaiting(List<Vehicle> atNodes) {
        Set<VehicleId> present = new HashSet<>();
        for (Vehicle vehicle : atNodes) {
            if (!vehicle.mayDepartAt(tick)) {
                continue;
            }
            present.add(vehicle.id());
            waitTicksByVehicle.merge(vehicle.id(), 1, Integer::sum);
        }
        waitTicksByVehicle.keySet().removeIf(id -> !present.contains(id));
    }

    private Map<EdgeId, Integer> waitingForSignals() {
        Map<EdgeId, Integer> waiting = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.arrived() || !vehicle.mayDepartAt(tick)) {
                continue;
            }
            if (!(vehicle.position() instanceof VehiclePosition.AtNode)) {
                continue;
            }
            vehicle.peekNextEdge().ifPresent(edgeId ->
                    waiting.merge(edgeId, 1, Integer::sum));
        }
        return waiting;
    }

    private Map<EdgeId, Integer> waitAgeByEdge() {
        Map<EdgeId, Integer> ages = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.arrived() || !vehicle.mayDepartAt(tick)) {
                continue;
            }
            if (!(vehicle.position() instanceof VehiclePosition.AtNode)) {
                continue;
            }
            int age = waitTicksByVehicle.getOrDefault(vehicle.id(), 0);
            vehicle.peekNextEdge().ifPresent(edgeId ->
                    ages.merge(edgeId, age, Integer::sum));
        }
        return ages;
    }

    /** Max VIP+/emergency rank among ready waiters on each approach. */
    private Map<EdgeId, Integer> priorityByEdge() {
        Map<EdgeId, Integer> pri = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.arrived() || !vehicle.mayDepartAt(tick)) {
                continue;
            }
            if (!(vehicle.position() instanceof VehiclePosition.AtNode)) {
                continue;
            }
            ServiceClass sc = vehicle.serviceClass();
            if (!sc.getsSignalPrivilege()) {
                continue;
            }
            vehicle.peekNextEdge().ifPresent(edgeId ->
                    pri.merge(edgeId, sc.rank(), Math::max));
        }
        return pri;
    }

    /**
     * Civilians obey lights. VIP/emergency only stop when a higher-rank unit needs
     * the conflicting approach — otherwise they cut through.
     */
    private boolean signalAllows(Vehicle vehicle, EdgeId next) {
        if (!policy.honorPriority()) {
            return signals.isOpen(next);
        }
        return signals.allowsEntry(next, vehicle.serviceClass().rank());
    }

    private boolean needsReplan(Vehicle vehicle) {
        if (!vehicle.hasRemainingEdges()) {
            return true;
        }
        EdgeId next = vehicle.peekNextEdge().orElseThrow();
        if (traffic.isClosed(next)) {
            return true;
        }
        if (!policy.honorPriority()) {
            return false;
        }
        // Proactive diversion: any remaining hop on a hard corridor → replan now.
        return corridors.pathBlocked(vehicle.remainingEdgesView(), vehicle.serviceClass())
                || corridors.blocks(next, vehicle.serviceClass());
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
