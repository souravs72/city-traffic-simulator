package com.traffic.rules;

import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * When the planned next road is blocked (accident ✕ / priority corridor), compute a new path.
 */
public final class Replanner {

    private final Router router;
    private final Function<Vehicle, EdgeCost> costFor;

    public static Replanner withFixedCost(Router router, EdgeCost cost) {
        Objects.requireNonNull(cost, "cost");
        Function<Vehicle, EdgeCost> fixed = (Vehicle vehicle) -> cost;
        return new Replanner(router, fixed);
    }

    public Replanner(Router router, Function<Vehicle, EdgeCost> costFor) {
        this.router = Objects.requireNonNull(router, "router");
        this.costFor = Objects.requireNonNull(costFor, "costFor");
    }

    public Optional<Path> computePath(Vehicle vehicle, RoadGraph graph) {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(graph, "graph");
        if (vehicle.arrived()) {
            return Optional.empty();
        }
        if (!(vehicle.position() instanceof VehiclePosition.AtNode at)) {
            return Optional.empty();
        }
        NodeId here = at.node();
        return router.findPath(graph, here, vehicle.destination(), costFor.apply(vehicle));
    }

    public boolean replan(Vehicle vehicle, RoadGraph graph) {
        Optional<Path> path = computePath(vehicle, graph);
        if (path.isEmpty()) {
            return false;
        }
        vehicle.replaceRemainingPath(path.get());
        return true;
    }
}
