package com.traffic.sim;

import com.traffic.config.SimConfig;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.vehicle.CarNames;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.RouteEstimator;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
        return spawn(graph, config, cost, trips, 0);
    }

    public static List<Vehicle> spawn(
            RoadGraph graph,
            SimConfig config,
            EdgeCost cost,
            List<Trip> trips,
            int spawnedAtTick
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(trips, "trips");

        Router router = Routers.create(config.routingAlgorithm(), graph);
        EdgeCost shortestCost = EdgeCost.baseWeight();
        Path[] paths = new Path[trips.size()];
        int[] shortestTicks = new int[trips.size()];
        int[] liveTicks = new int[trips.size()];

        IntStream indices = IntStream.range(0, trips.size());
        if (config.useParallelRouting(trips.size())) {
            indices = indices.parallel();
        }
        indices.forEach(i -> {
            Trip trip = trips.get(i);
            Path live = router.findPath(graph, trip.start(), trip.goal(), cost)
                    .orElseThrow(() -> new IllegalStateException(
                            "No path for " + trip.nickname() + " "
                                    + trip.start() + "→" + trip.goal()));
            paths[i] = live;
            liveTicks[i] = RouteEstimator.fromPath(graph, live).travelTicks();
            shortestTicks[i] = RouteEstimator.estimate(
                            graph, router, trip.start(), trip.goal(), shortestCost)
                    .map(RouteEstimator.Estimate::travelTicks)
                    .orElse(liveTicks[i]);
        });

        List<Vehicle> fleet = new ArrayList<>(trips.size());
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            fleet.add(new Vehicle(
                    new VehicleId(i),
                    trip.start(),
                    trip.goal(),
                    config.initialFuel(),
                    paths[i],
                    shortestTicks[i],
                    liveTicks[i],
                    spawnedAtTick,
                    trip.nickname()
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
                    CarNames.forIndex(n.getAndIncrement())
            ));
        }
        if (trips.size() < count) {
            throw new IllegalStateException("Could not sample enough distinct trips");
        }
        return List.copyOf(trips);
    }

    /**
     * Google-Maps-style commute: origins near the map rim, goals near the centroid.
     */
    public static List<Trip> commuteTrips(RoadGraph graph, int count, long seed) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        List<Node> nodes = new ArrayList<>();
        graph.nodes().forEach(nodes::add);
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 nodes for rush hour");
        }
        double cx = 0;
        double cy = 0;
        for (Node n : nodes) {
            cx += n.x();
            cy += n.y();
        }
        cx /= nodes.size();
        cy /= nodes.size();
        final double fcx = cx;
        final double fcy = cy;
        List<Node> byDist = new ArrayList<>(nodes);
        byDist.sort(Comparator.comparingDouble(n -> -Math.hypot(n.x() - fcx, n.y() - fcy)));
        List<Node> homes = byDist.subList(0, Math.max(1, nodes.size() / 2));
        List<Node> jobs = new ArrayList<>(nodes);
        jobs.sort(Comparator.comparingDouble(n -> Math.hypot(n.x() - fcx, n.y() - fcy)));
        int jobCount = Math.max(1, Math.min(nodes.size() / 3, jobs.size()));
        jobs = jobs.subList(0, jobCount);

        Random rng = new Random(seed);
        AtomicInteger n = new AtomicInteger((int) Math.floorMod(seed, 97));
        List<Trip> trips = new ArrayList<>();
        int guard = 0;
        while (trips.size() < count && guard++ < count * 30) {
            Node start = homes.get(rng.nextInt(homes.size()));
            Node goal = jobs.get(rng.nextInt(jobs.size()));
            if (start.id().equals(goal.id())) {
                continue;
            }
            trips.add(new Trip(start.id(), goal.id(), CarNames.forIndex(n.getAndIncrement())));
        }
        if (trips.isEmpty()) {
            return randomTrips(graph, count, seed);
        }
        return List.copyOf(trips);
    }
}
