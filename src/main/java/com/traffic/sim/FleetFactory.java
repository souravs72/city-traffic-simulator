package com.traffic.sim;

import com.traffic.config.SimConfig;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Spawns fleets on any {@link RoadGraph} (demo map or giant grid). */
public final class FleetFactory {

    private FleetFactory() {
    }

    public record Trip(NodeId start, NodeId goal, String nickname) {
    }

    public static List<Vehicle> spawn(
            RoadGraph graph,
            SimConfig config,
            EdgeCost cost,
            List<Trip> trips
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(trips, "trips");

        Router router = Routers.create(config.routingAlgorithm(), graph);
        List<Vehicle> fleet = new ArrayList<>();
        int id = 0;
        for (Trip trip : trips) {
            Path path = router.findPath(graph, trip.start(), trip.goal(), cost)
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

    /** Backward-compatible helper for the small demo map. */
    public static List<Vehicle> spawn(
            DemoCity city,
            SimConfig config,
            EdgeCost cost,
            List<Trip> trips
    ) {
        return spawn(city.graph, config, cost, trips);
    }

    public static List<Trip> defaultTrips(DemoCity city) {
        return List.of(
                new Trip(city.a, city.c, "Harbor Hopper"),
                new Trip(city.a, city.c, "Shortcut Seeker"),
                new Trip(city.b, city.c, "Market Runner"),
                new Trip(city.a, city.d, "Park Pilgrim")
        );
    }

    /** Random OD pairs across a big graph (seeded for replayable play). */
    public static List<Trip> randomTrips(RoadGraph graph, int count, long seed) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        List<Node> nodes = new ArrayList<>();
        graph.nodes().forEach(nodes::add);
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 nodes to spawn trips");
        }
        Random rng = new Random(seed);
        AtomicInteger n = new AtomicInteger();
        List<Trip> trips = new ArrayList<>();
        int guard = 0;
        while (trips.size() < count && guard++ < count * 20) {
            Node start = nodes.get(rng.nextInt(nodes.size()));
            Node goal = nodes.get(rng.nextInt(nodes.size()));
            if (start.id().equals(goal.id())) {
                continue;
            }
            trips.add(new Trip(
                    start.id(),
                    goal.id(),
                    "Cruise-" + n.getAndIncrement()
            ));
        }
        if (trips.size() < count) {
            throw new IllegalStateException("Could not sample enough distinct trips");
        }
        return List.copyOf(trips);
    }
}
