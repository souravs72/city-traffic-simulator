package com.traffic.model.vehicle;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.NodeId;
import com.traffic.routing.Path;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A car: fuel, destination, planned remaining edges, priority class, and current position.
 * Mutation is intentional — the tick loop advances state each step.
 */
public final class Vehicle {

    private final VehicleId id;
    private final String name;
    private final NodeId origin;
    private final NodeId destination;
    private final ServiceClass serviceClass;
    private final int plannedShortestTicks;
    private final int plannedLiveTicks;
    private int spawnedAtTick;
    /** World tick at which this vehicle may first enter a road (VIP fixed departures). */
    private int scheduledDepartAtTick;
    private final int initialFuel;
    private int fuel;
    private int fuelBurned;
    private VehiclePosition position;
    private final Deque<EdgeId> remainingEdges = new ArrayDeque<>();
    private boolean arrived;
    private int replanCount;
    private Integer arrivedAtTick;

    public Vehicle(VehicleId id, NodeId start, NodeId destination, int initialFuel, Path path) {
        this(id, start, destination, initialFuel, path, 0, 0, 0, CarNames.forId(id.value()),
                ServiceClass.CIVILIAN, 0);
    }

    public Vehicle(
            VehicleId id,
            NodeId start,
            NodeId destination,
            int initialFuel,
            Path path,
            int plannedShortestTicks,
            int plannedLiveTicks,
            int spawnedAtTick
    ) {
        this(id, start, destination, initialFuel, path, plannedShortestTicks, plannedLiveTicks, spawnedAtTick,
                CarNames.forId(id.value()), ServiceClass.CIVILIAN, spawnedAtTick);
    }

    public Vehicle(
            VehicleId id,
            NodeId start,
            NodeId destination,
            int initialFuel,
            Path path,
            int plannedShortestTicks,
            int plannedLiveTicks,
            int spawnedAtTick,
            String name
    ) {
        this(id, start, destination, initialFuel, path, plannedShortestTicks, plannedLiveTicks, spawnedAtTick,
                name, ServiceClass.CIVILIAN, spawnedAtTick);
    }

    public Vehicle(
            VehicleId id,
            NodeId start,
            NodeId destination,
            int initialFuel,
            Path path,
            int plannedShortestTicks,
            int plannedLiveTicks,
            int spawnedAtTick,
            String name,
            ServiceClass serviceClass
    ) {
        this(id, start, destination, initialFuel, path, plannedShortestTicks, plannedLiveTicks, spawnedAtTick,
                name, serviceClass, spawnedAtTick);
    }

    public Vehicle(
            VehicleId id,
            NodeId start,
            NodeId destination,
            int initialFuel,
            Path path,
            int plannedShortestTicks,
            int plannedLiveTicks,
            int spawnedAtTick,
            String name,
            ServiceClass serviceClass,
            int scheduledDepartAtTick
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.origin = Objects.requireNonNull(start, "start");
        this.destination = Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(path, "path");
        this.serviceClass = serviceClass == null ? ServiceClass.CIVILIAN : serviceClass;
        if (initialFuel < 0) {
            throw new IllegalArgumentException("initialFuel must be >= 0");
        }
        if (plannedShortestTicks < 0 || plannedLiveTicks < 0) {
            throw new IllegalArgumentException("planned ticks must be >= 0");
        }
        if (spawnedAtTick < 0 || scheduledDepartAtTick < 0) {
            throw new IllegalArgumentException("ticks must be >= 0");
        }
        this.name = (name == null || name.isBlank()) ? CarNames.forId(id.value()) : name.trim();
        this.plannedShortestTicks = plannedShortestTicks;
        this.plannedLiveTicks = plannedLiveTicks;
        this.spawnedAtTick = spawnedAtTick;
        this.scheduledDepartAtTick = scheduledDepartAtTick;
        this.initialFuel = initialFuel;
        this.fuel = initialFuel;
        this.fuelBurned = 0;
        this.position = new VehiclePosition.AtNode(start);
        this.remainingEdges.addAll(path.edges());
        this.arrived = start.equals(destination) && path.isEmpty();
        this.replanCount = 0;
        this.arrivedAtTick = this.arrived ? spawnedAtTick : null;
    }

    public VehicleId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ServiceClass serviceClass() {
        return serviceClass;
    }

    public NodeId origin() {
        return origin;
    }

    public NodeId destination() {
        return destination;
    }

    public int plannedShortestTicks() {
        return plannedShortestTicks;
    }

    public int plannedLiveTicks() {
        return plannedLiveTicks;
    }

    public int spawnedAtTick() {
        return spawnedAtTick;
    }

    public int scheduledDepartAtTick() {
        return scheduledDepartAtTick;
    }

    public boolean mayDepartAt(int worldTick) {
        return worldTick >= scheduledDepartAtTick;
    }

    public Optional<Integer> arrivedAtTick() {
        return Optional.ofNullable(arrivedAtTick);
    }

    public Optional<Integer> actualTicks() {
        return arrivedAtTick == null
                ? Optional.empty()
                : Optional.of(arrivedAtTick - spawnedAtTick);
    }

    public void noteArrival(int worldTick) {
        if (arrived && arrivedAtTick == null) {
            arrivedAtTick = worldTick;
        }
    }

    public void armRace(Path path, int raceStartTick) {
        Objects.requireNonNull(path, "path");
        if (raceStartTick < 0) {
            throw new IllegalArgumentException("raceStartTick must be >= 0");
        }
        this.position = new VehiclePosition.AtNode(origin);
        this.remainingEdges.clear();
        this.remainingEdges.addAll(path.edges());
        this.fuel = initialFuel;
        this.fuelBurned = 0;
        this.arrived = origin.equals(destination) && path.isEmpty();
        this.replanCount = 0;
        this.spawnedAtTick = raceStartTick;
        // Preserve relative VIP hold: if scheduled after previous spawn, shift with race start.
        if (scheduledDepartAtTick < raceStartTick) {
            this.scheduledDepartAtTick = raceStartTick;
        }
        this.arrivedAtTick = this.arrived ? raceStartTick : null;
    }

    public int fuel() {
        return fuel;
    }

    public int fuelBurned() {
        return fuelBurned;
    }

    public int fuelLedger() {
        return fuel + fuelBurned;
    }

    public VehiclePosition position() {
        return position;
    }

    public boolean arrived() {
        return arrived;
    }

    public int replanCount() {
        return replanCount;
    }

    public boolean hasRemainingEdges() {
        return !remainingEdges.isEmpty();
    }

    public Optional<EdgeId> peekNextEdge() {
        return Optional.ofNullable(remainingEdges.peekFirst());
    }

    public Optional<NodeId> currentNode() {
        if (position instanceof VehiclePosition.AtNode at) {
            return Optional.of(at.node());
        }
        return Optional.empty();
    }

    public void replaceRemainingPath(Path path) {
        Objects.requireNonNull(path, "path");
        if (!(position instanceof VehiclePosition.AtNode at)) {
            throw new IllegalStateException("Can only replan while at a node");
        }
        remainingEdges.clear();
        remainingEdges.addAll(path.edges());
        replanCount++;
        if (at.node().equals(destination) && remainingEdges.isEmpty()) {
            arrived = true;
        }
    }

    public void snapToNode(NodeId node) {
        Objects.requireNonNull(node, "node");
        this.position = new VehiclePosition.AtNode(node);
        if (node.equals(destination) && remainingEdges.isEmpty()) {
            arrived = true;
        }
    }

    public void enterEdge(EdgeId edge, int travelTicks) {
        Objects.requireNonNull(edge, "edge");
        if (travelTicks <= 0) {
            throw new IllegalArgumentException("travelTicks must be > 0");
        }
        EdgeId next = remainingEdges.pollFirst();
        if (next == null || !next.equals(edge)) {
            throw new IllegalStateException("Cannot enter edge " + edge + "; next was " + next);
        }
        this.position = new VehiclePosition.OnEdge(edge, travelTicks);
    }

    public boolean advanceOnEdge() {
        if (!(position instanceof VehiclePosition.OnEdge onEdge)) {
            throw new IllegalStateException("Vehicle is not on an edge");
        }
        if (fuel <= 0) {
            return false;
        }
        fuel--;
        fuelBurned++;
        int left = onEdge.ticksRemaining() - 1;
        if (left > 0) {
            position = new VehiclePosition.OnEdge(onEdge.edge(), left);
            return false;
        }
        return true;
    }

    public void finishEdgeAt(NodeId node) {
        Objects.requireNonNull(node, "node");
        position = new VehiclePosition.AtNode(node);
        if (node.equals(destination) && remainingEdges.isEmpty()) {
            arrived = true;
        }
    }

    public List<EdgeId> remainingEdgesView() {
        return List.copyOf(remainingEdges);
    }
}
