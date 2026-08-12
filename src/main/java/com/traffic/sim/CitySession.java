package com.traffic.sim;

import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.priority.VipLockdown;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.CarNames;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.RouteEstimator;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.rules.DynamicEdgeCost;
import com.traffic.rules.PriorityEdgeCost;
import com.traffic.rules.Replanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UI-facing session: edit an {@link EditableCity} in {@link SessionMode#BUILD},
 * then {@link #play()} / {@link #applyEdits()} to snapshot the map and replan the fleet.
 */
public final class CitySession {

    private final SimConfig config;
    private final EditableCity city;
    private SessionMode mode;
    private TrafficState traffic;
    private SignalNetwork signals;
    private List<Vehicle> fleet;
    private Simulation simulation;
    private Replanner replanner;
    private final CorridorBoard corridors = new CorridorBoard();
    private ControlPolicy controlPolicy = ControlPolicy.CITY_FLOW;
    private int worldTick;
    private int mapVersionApplied;
    private int corridorSeq;

    private CitySession(SimConfig config, EditableCity city, List<Vehicle> fleet) {
        this.config = Objects.requireNonNull(config, "config");
        this.city = Objects.requireNonNull(city, "city");
        this.mode = SessionMode.BUILD;
        this.fleet = new ArrayList<>(Objects.requireNonNull(fleet, "fleet"));
        this.worldTick = 0;
        this.signals = SignalNetwork.none();
        this.mapVersionApplied = -1;
        applyEdits();
        this.mode = SessionMode.BUILD;
    }

    public static CitySession openBlank(SimConfig config) {
        return new CitySession(config, new EditableCity(), List.of());
    }

    /** Busy irregular megacity (organic street fabric). */
    public static CitySession openOrganic(SimConfig config, int fleetSize, long seed) {
        EditableCity city = OrganicCityGenerator.generate(seed);
        FacilitySeeder.seed(city, seed);
        RoadGraph graph = city.snapshot();
        TrafficState bootstrap = new TrafficState(graph);
        EdgeCost cost = new DynamicEdgeCost(bootstrap, config.congestionPenaltyPerCar());
        List<Vehicle> fleet;
        if (fleetSize <= 0) {
            fleet = List.of();
        } else {
            List<FleetFactory.Trip> trips = FleetFactory.randomTrips(graph, fleetSize, seed);
            fleet = FleetFactory.spawn(graph, config, cost, trips);
        }
        return new CitySession(config, city, fleet);
    }

    public static CitySession openGrid(SimConfig config, CityGenConfig genConfig, int fleetSize, long seed) {
        EditableCity city = GridCityGenerator.generate(genConfig);
        FacilitySeeder.seed(city, seed);
        RoadGraph graph = city.snapshot();
        TrafficState bootstrap = new TrafficState(graph);
        EdgeCost cost = new DynamicEdgeCost(bootstrap, config.congestionPenaltyPerCar());
        List<Vehicle> fleet;
        if (fleetSize <= 0) {
            fleet = List.of();
        } else {
            List<FleetFactory.Trip> trips = FleetFactory.randomTrips(graph, fleetSize, seed);
            fleet = FleetFactory.spawn(graph, config, cost, trips);
        }
        return new CitySession(config, city, fleet);
    }

    public SimConfig config() {
        return config;
    }

    public EditableCity city() {
        return city;
    }

    public SessionMode mode() {
        return mode;
    }

    public TrafficState traffic() {
        return traffic;
    }

    public List<Vehicle> fleet() {
        return List.copyOf(fleet);
    }

    public Simulation simulation() {
        return simulation;
    }

    public CorridorBoard corridors() {
        return corridors;
    }

    public ControlPolicy controlPolicy() {
        return controlPolicy;
    }

    public void setControlPolicy(ControlPolicy policy) {
        this.controlPolicy = policy == null ? ControlPolicy.CITY_FLOW : policy;
        if (simulation != null) {
            rebuildSimulation();
        }
    }

    public int worldTick() {
        return worldTick;
    }

    public void build() {
        mode = SessionMode.BUILD;
    }

    /**
     * Start the race: apply map edits, reset clock to 0s, put every car back at its
     * origin with a fresh route. All cars share start time 0.
     */
    public void play() {
        applyEdits();
        worldTick = 0;
        RoadGraph graph = traffic.graph();
        Router router = Routers.create(config.routingAlgorithm(), graph);
        for (Vehicle vehicle : fleet) {
            Path live = router.findPath(
                            graph,
                            vehicle.origin(),
                            vehicle.destination(),
                            edgeCostFor(vehicle))
                    .orElseThrow(() -> new IllegalStateException(
                            "No path for car #" + vehicle.id().value()
                                    + " " + vehicle.origin() + "→" + vehicle.destination()));
            vehicle.armRace(live, 0);
            // VIP corridors re-armed for race start relative to scheduled tick.
            if (vehicle.serviceClass() == ServiceClass.VIP
                    && vehicle.scheduledDepartAtTick() > 0
                    && controlPolicy.honorPriority()) {
                armVipLockdown(vehicle, vehicle.scheduledDepartAtTick());
            }
        }
        rebuildSimulation();
        mode = SessionMode.PLAY;
    }

    /** Place an intersection on the blank canvas (BUILD). */
    public com.traffic.model.graph.Node addNode(double x, double y, String label) {
        return addNode(x, y, label, FacilityKind.NONE);
    }

    public com.traffic.model.graph.Node addNode(double x, double y, String label, FacilityKind facility) {
        if (mode != SessionMode.BUILD) {
            throw new IllegalStateException("Add nodes only in BUILD mode — pause the clock first");
        }
        FacilityKind kind = facility == null ? FacilityKind.NONE : facility;
        if (label == null || label.isBlank()) {
            return city.addIntersection(x, y, "X" + city.nodeCount(), kind);
        }
        return city.addIntersection(x, y, label, kind);
    }

    public com.traffic.model.graph.Node setFacility(NodeId nodeId, FacilityKind facility) {
        requireBuild();
        return city.setFacility(nodeId, facility);
    }

    /** Delete an intersection and every road touching it; drop cars that used it. */
    public void removeNode(NodeId nodeId) {
        requireBuild();
        Objects.requireNonNull(nodeId, "nodeId");
        fleet.removeIf(v ->
                v.origin().equals(nodeId)
                        || v.destination().equals(nodeId)
                        || v.currentNode().map(nodeId::equals).orElse(false)
                        || (v.position() instanceof VehiclePosition.OnEdge on
                        && traffic != null
                        && traffic.graph().edge(on.edge())
                        .map(e -> e.from().equals(nodeId) || e.to().equals(nodeId))
                        .orElse(false)));
        city.removeIntersection(nodeId);
    }

    /** Delete a road (and the reverse direction if present). */
    public void removeRoad(com.traffic.model.graph.EdgeId edgeId) {
        requireBuild();
        Objects.requireNonNull(edgeId, "edgeId");
        var edge = city.edge(edgeId).orElseThrow(() -> new IllegalArgumentException("Unknown edge: " + edgeId));
        city.removeEdge(edgeId);
        city.findEdge(edge.to(), edge.from()).ifPresent(rev -> city.removeEdge(rev.id()));
    }

    private void requireBuild() {
        if (mode != SessionMode.BUILD) {
            throw new IllegalStateException("Edit the map only in BUILD mode — pause the clock first");
        }
    }

    /**
     * Snapshot {@link EditableCity} → new {@link RoadGraph}, migrate cars off deleted roads,
     * replan everyone. Safe to call from Build or before Play.
     */
    public int applyEdits() {
        evacuateFleetToNodes();

        RoadGraph fresh = city.snapshot();
        this.traffic = new TrafficState(fresh);
        this.replanner = new Replanner(
                Routers.create(config.routingAlgorithm(), fresh),
                this::edgeCostFor
        );
        this.signals = GridSignalPlanner.forGraph(fresh, config.lightTiming());

        List<Vehicle> needReplan = new ArrayList<>();
        for (Vehicle vehicle : fleet) {
            if (!vehicle.arrived()) {
                needReplan.add(vehicle);
            }
        }

        int replans = 0;
        if (config.useParallelRouting(needReplan.size())) {
            ConcurrentHashMap<VehicleId, Optional<Path>> computed = new ConcurrentHashMap<>();
            needReplan.parallelStream().forEach(vehicle ->
                    computed.put(vehicle.id(), replanner.computePath(vehicle, fresh)));
            for (Vehicle vehicle : needReplan) {
                Optional<Path> path = computed.getOrDefault(vehicle.id(), Optional.empty());
                if (path.isPresent()) {
                    int before = vehicle.replanCount();
                    vehicle.replaceRemainingPath(path.get());
                    if (vehicle.replanCount() > before) {
                        replans++;
                    }
                }
            }
        } else {
            for (Vehicle vehicle : needReplan) {
                int before = vehicle.replanCount();
                if (replanner.replan(vehicle, fresh) && vehicle.replanCount() > before) {
                    replans++;
                }
            }
        }

        rebuildSimulation();
        this.mapVersionApplied = city.version();
        return replans;
    }

    private EdgeCost edgeCostFor(Vehicle vehicle) {
        return new PriorityEdgeCost(
                traffic,
                corridors,
                vehicle.serviceClass(),
                config.congestionPenaltyPerCar(),
                controlPolicy.honorPriority()
        );
    }

    /**
     * Add a car from {@code from} → {@code to}, routed with live congestion costs.
     * Records shortest-path vs congestion-aware travel ticks at spawn.
     * Blank {@code name} falls back to a playful unused name.
     */
    public Vehicle addTrip(NodeId from, NodeId to) {
        return addTrip(from, to, null, ServiceClass.CIVILIAN, 0);
    }

    public Vehicle addTrip(NodeId from, NodeId to, String name) {
        return addTrip(from, to, name, ServiceClass.CIVILIAN, 0);
    }

    public Vehicle addTrip(NodeId from, NodeId to, String name, ServiceClass serviceClass) {
        return addTrip(from, to, name, serviceClass, 0);
    }

    /**
     * Add a trip. VIP with {@code scheduledDepartAtTick > 0} clears a corridor and holds until that tick.
     */
    public Vehicle addTrip(
            NodeId from,
            NodeId to,
            String name,
            ServiceClass serviceClass,
            int scheduledDepartAtTick
    ) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        ServiceClass sc = serviceClass == null ? ServiceClass.CIVILIAN : serviceClass;
        if (from.equals(to)) {
            throw new IllegalArgumentException("Start and end must differ");
        }
        if (scheduledDepartAtTick < 0) {
            throw new IllegalArgumentException("scheduledDepartAtTick must be >= 0");
        }
        if (sc != ServiceClass.VIP && scheduledDepartAtTick > 0) {
            throw new IllegalArgumentException("Only VIP trips may use a fixed departure time");
        }
        if (hasUnappliedEdits()) {
            applyEdits();
        }
        RoadGraph graph = traffic.graph();
        graph.requireNode(from);
        graph.requireNode(to);

        Router router = Routers.create(config.routingAlgorithm(), graph);
        EdgeCost liveCost = new PriorityEdgeCost(
                traffic, corridors, sc, config.congestionPenaltyPerCar(), controlPolicy.honorPriority());
        Path live = router.findPath(graph, from, to, liveCost).orElse(null);
        if (live == null) {
            boolean topoOk = router.findPath(graph, from, to, EdgeCost.baseWeight()).isPresent();
            if (!topoOk) {
                throw new IllegalArgumentException(
                        "No road connects those two places — try another pair (or add a road).");
            }
            throw new IllegalArgumentException(
                    "That route is blocked right now (VIP/emergency lock or accident). "
                            + "Pick another end, or wait for the corridor to clear.");
        }
        int liveTicks = RouteEstimator.fromPath(graph, live).travelTicks();
        int shortestTicks = RouteEstimator.estimate(graph, router, from, to, EdgeCost.baseWeight())
                .map(RouteEstimator.Estimate::travelTicks)
                .orElse(liveTicks);

        int nextId = fleet.stream().mapToInt(v -> v.id().value()).max().orElse(-1) + 1;
        String displayName = (name == null || name.isBlank())
                ? defaultNameFor(sc)
                : name.trim();
        Vehicle car = new Vehicle(
                new VehicleId(nextId),
                from,
                to,
                config.initialFuel(),
                live,
                shortestTicks,
                liveTicks,
                0,
                displayName,
                sc,
                scheduledDepartAtTick
        );
        fleet.add(car);
        if (sc == ServiceClass.VIP && scheduledDepartAtTick > 0 && controlPolicy.honorPriority()) {
            armVipLockdown(car, scheduledDepartAtTick);
        }
        rebuildSimulation();
        return car;
    }

    private String defaultNameFor(ServiceClass sc) {
        return switch (sc) {
            case FIRE -> "Fire-" + (fleet.size() + 1);
            case AMBULANCE -> "Ambulance-" + (fleet.size() + 1);
            case POLICE -> "Police-" + (fleet.size() + 1);
            case VIP -> "VIP-" + (fleet.size() + 1);
            case CIVILIAN -> CarNames.pickUnused(fleet.stream().map(Vehicle::name).toList());
        };
    }

        /**
     * Strong VIP lockdown: hard-close spine + junction approaches, soft-tax a buffer ring,
     * then replan civilians onto bypass routes.
     */
    public void armVipLockdown(Vehicle vip, int departAt) {
        List<EdgeId> edges = vip.remainingEdgesView();
        if (edges.isEmpty() || !controlPolicy.honorPriority()) {
            return;
        }
        int lead = 3;
        int start = Math.max(0, departAt - lead);
        int end = departAt + Math.max(16, edges.size() * 5);
        VipLockdown.Plan plan = VipLockdown.plan(traffic.graph(), edges);
        corridors.activate(new CorridorBoard.Corridor(
                "vip-" + (++corridorSeq),
                ServiceClass.VIP,
                plan.hardClosed(),
                plan.softBuffer(),
                start,
                end
        ));
        corridors.setCurrentTick(worldTick);
        replanAroundCorridors();
    }

    /** Replan any non-emergency vehicle whose remaining path hits a hard corridor. */
    public int replanAroundCorridors() {
        if (!controlPolicy.honorPriority() || replanner == null) {
            return 0;
        }
        int n = 0;
        for (Vehicle vehicle : fleet) {
            if (vehicle.arrived() || vehicle.serviceClass().isEmergency()) {
                continue;
            }
            if (vehicle.serviceClass() == ServiceClass.VIP) {
                continue;
            }
            if (!(vehicle.position() instanceof VehiclePosition.AtNode)) {
                continue;
            }
            if (!corridors.pathBlocked(vehicle.remainingEdgesView(), vehicle.serviceClass())) {
                continue;
            }
            int before = vehicle.replanCount();
            if (replanner.replan(vehicle, traffic.graph()) && vehicle.replanCount() > before) {
                n++;
            }
        }
        return n;
    }

    /**
     * Dispatch FIRE / AMBULANCE / POLICE from the nearest matching facility to a scene.
     */
    public Vehicle dispatch(ServiceClass serviceClass, NodeId scene) {
        Objects.requireNonNull(serviceClass, "serviceClass");
        Objects.requireNonNull(scene, "scene");
        if (!serviceClass.isEmergency()) {
            throw new IllegalArgumentException("dispatch requires FIRE, AMBULANCE, or POLICE");
        }
        if (hasUnappliedEdits()) {
            applyEdits();
        }
        FacilityKind want = switch (serviceClass) {
            case FIRE -> FacilityKind.FIRE_STATION;
            case AMBULANCE -> FacilityKind.HOSPITAL;
            case POLICE -> FacilityKind.POLICE_STATION;
            default -> FacilityKind.NONE;
        };
        Node origin = nearestFacility(want, scene);
        if (origin == null) {
            throw new IllegalStateException("No " + want.name() + " facility on the map — place one first");
        }
        return addTrip(origin.id(), scene, null, serviceClass, 0);
    }

    private Node nearestFacility(FacilityKind kind, NodeId near) {
        Node target = traffic.graph().requireNode(near);
        Node best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Node n : city.nodes()) {
            if (n.facility() != kind) {
                continue;
            }
            double d = Math.hypot(n.x() - target.x(), n.y() - target.y());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /**
     * VIP motorcade: VIP + police escorts, shared timed lockdown corridor.
     */
    public List<Vehicle> scheduleVipConvoy(NodeId from, NodeId to, int departAtTick, int escorts) {
        if (escorts < 0) {
            throw new IllegalArgumentException("escorts must be >= 0");
        }
        int depart = Math.max(0, departAtTick);
        Vehicle vip = addTrip(from, to, null, ServiceClass.VIP, depart);
        List<Vehicle> out = new ArrayList<>();
        out.add(vip);
        for (int i = 0; i < escorts; i++) {
            out.add(addTrip(from, to, "Escort-" + (i + 1), ServiceClass.POLICE, 0));
        }
        // Re-arm a single strong lockdown from the VIP path (escorts may share it).
        armVipLockdown(vip, depart);
        return List.copyOf(out);
    }

    /** Seed facilities on the current blank/generated map if none exist. */
    public void ensureFacilities(long seed) {
        FacilitySeeder.seed(city, seed);
        if (hasUnappliedEdits()) {
            applyEdits();
        }
    }

    /** Spawn one random origin→destination trip (seeded from world tick + fleet size). */
    public Vehicle addRandomTrip() {
        RoadGraph graph = traffic.graph();
        long seed = worldTick * 31L + fleet.size() + 17L;
        FleetFactory.Trip trip = FleetFactory.randomTrips(graph, 1, seed).get(0);
        return addTrip(trip.start(), trip.goal(), trip.nickname());
    }

    /** Commute wave: homes on the rim → jobs near the map center. */
    public List<Vehicle> addRushHour(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        if (hasUnappliedEdits()) {
            applyEdits();
        }
        RoadGraph graph = traffic.graph();
        long seed = worldTick * 97L + fleet.size() * 13L + 42L;
        List<FleetFactory.Trip> trips = FleetFactory.commuteTrips(graph, count, seed);
        List<Vehicle> added = new ArrayList<>();
        for (FleetFactory.Trip trip : trips) {
            try {
                added.add(addTrip(trip.start(), trip.goal(), trip.nickname()));
            } catch (RuntimeException ignored) {
                // corridor/accident may block a sampled pair — keep filling the wave
            }
        }
        if (added.isEmpty()) {
            throw new IllegalArgumentException(
                    "Couldn't place rush-hour trips — map gap or active road locks. "
                            + "Add a connecting road, clear locks, or regenerate.");
        }
        return List.copyOf(added);
    }

    public boolean hasUnappliedEdits() {
        return city.version() != mapVersionApplied;
    }

    public void step() {
        requirePlay();
        if (hasUnappliedEdits()) {
            applyEdits();
        }
        simulation.step();
        worldTick++;
        for (Vehicle vehicle : fleet) {
            vehicle.noteArrival(worldTick);
        }
    }

    public int run(int maxTicks) {
        requirePlay();
        int started = worldTick;
        while (worldTick - started < maxTicks && !simulation.allArrived()) {
            step();
        }
        return worldTick - started;
    }

    public long arrivedCount() {
        return simulation.arrivedCount();
    }

    public boolean allArrived() {
        return simulation.allArrived();
    }

    private void rebuildSimulation() {
        this.simulation = new Simulation(
                traffic,
                signals,
                fleet,
                config.initialFuel(),
                replanner,
                true,
                config.parallelRoutingThreshold(),
                corridors,
                controlPolicy
        );
    }

    private void evacuateFleetToNodes() {
        if (traffic == null) {
            return;
        }
        RoadGraph old = traffic.graph();
        for (Vehicle vehicle : fleet) {
            if (vehicle.position() instanceof VehiclePosition.OnEdge onEdge) {
                Edge edge = old.edge(onEdge.edge()).orElse(null);
                if (edge != null && traffic.occupancy(onEdge.edge()) > 0) {
                    traffic.leave(onEdge.edge());
                }
                if (edge != null && old.node(edge.to()).isPresent()) {
                    vehicle.snapToNode(edge.to());
                } else if (edge != null && old.node(edge.from()).isPresent()) {
                    vehicle.snapToNode(edge.from());
                } else {
                    vehicle.snapToNode(city.nodes().iterator().next().id());
                }
            }
        }
    }

    private void requirePlay() {
        if (mode != SessionMode.PLAY) {
            throw new IllegalStateException("Session is in BUILD mode — call play() first");
        }
    }
}
