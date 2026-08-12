package com.traffic.sim;

import com.traffic.config.SimConfig;
import com.traffic.model.graph.NodeId;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Spawns a playful fleet with OD pairs across the demo city. */
public final class FleetFactory {

    private FleetFactory() {
    }

    public record Trip(NodeId start, NodeId goal, String nickname) {
    }

    public static List<Vehicle> spawn(
            DemoCity city,
            SimConfig config,
            EdgeCost cost,
            List<Trip> trips
    ) {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(trips, "trips");

        Router router = Routers.create(config.routingAlgorithm(), city.graph);
        List<Vehicle> fleet = new ArrayList<>();
        int id = 0;
        for (Trip trip : trips) {
            Path path = router.findPath(city.graph, trip.start(), trip.goal(), cost)
                    .orElseThrow(() -> new IllegalStateException(
                            "No path for " + trip.nickname() + " "
                                    + trip.start() + "→" + trip.goal()));
            fleet.add(new Vehicle(
                    new VehicleId(id++),
                    trip.start(),
                    trip.goal(),
                    config.initialFuel(),
                    path
            ));
        }
        return List.copyOf(fleet);
    }

    public static List<Trip> defaultTrips(DemoCity city) {
        return List.of(
                new Trip(city.a, city.c, "Harbor Hopper"),
                new Trip(city.a, city.c, "Shortcut Seeker"),
                new Trip(city.b, city.c, "Market Runner"),
                new Trip(city.a, city.d, "Park Pilgrim")
        );
    }
}
