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
 * A car: fuel, destination, planned remaining edges, and current position.
 * Mutation is intentional — the tick loop advances state each step.
 */
public final class Vehicle {

    private final VehicleId id;
    private final NodeId destination;
    private int fuel;
    private int fuelBurned;
    private VehiclePosition position;
    private final Deque<EdgeId> remainingEdges = new ArrayDeque<>();
    private boolean arrived;
    private int replanCount;

    public Vehicle(VehicleId id, NodeId start, NodeId destination, int initialFuel, Path path) {
        this.id = Objects.requireNonNull(id, "id");
        this.destination = Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(path, "path");
        if (initialFuel < 0) {
            throw new IllegalArgumentException("initialFuel must be >= 0");
        }
        this.fuel = initialFuel;
        this.fuelBurned = 0;
        this.position = new VehiclePosition.AtNode(start);
        this.remainingEdges.addAll(path.edges());
        this.arrived = start.equals(destination) && path.isEmpty();
        this.replanCount = 0;
    }

    public VehicleId id() {
        return id;
    }

    public NodeId destination() {
        return destination;
    }

    public int fuel() {
        return fuel;
    }

    public int fuelBurned() {
        return fuelBurned;
    }

    /** fuel + burned — should stay equal to initial fuel if conservation holds. */
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

    /**
     * Swap the remaining itinerary (used after an accident blocks the old plan).
     * Only legal while sitting at a node.
     */
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

    /** One tick of travel on the current edge. Returns true if the edge is finished this tick. */
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
