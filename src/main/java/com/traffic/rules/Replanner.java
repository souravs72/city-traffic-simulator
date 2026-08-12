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

/**
 * When the planned next road is blocked (accident ✕), compute a new path from here.
 */
public final class Replanner {

    private final Router router;
    private final EdgeCost cost;

    public Replanner(Router router, EdgeCost cost) {
        this.router = Objects.requireNonNull(router, "router");
        this.cost = Objects.requireNonNull(cost, "cost");
    }

    /**
     * Replace the vehicle's remaining route if a path exists.
     * @return true if the route changed
     */
    public boolean replan(Vehicle vehicle, RoadGraph graph) {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(graph, "graph");
        if (vehicle.arrived()) {
            return false;
        }
        if (!(vehicle.position() instanceof VehiclePosition.AtNode at)) {
            return false;
        }
        NodeId here = at.node();
        Optional<Path> path = router.findPath(graph, here, vehicle.destination(), cost);
        if (path.isEmpty()) {
            return false;
        }
        vehicle.replaceRemainingPath(path.get());
        return true;
    }
}
