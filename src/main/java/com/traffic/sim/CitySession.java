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
import com.traffic.model.priority.PriorityMechanisms;
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
import com.traffic.sim.parallel.SimExecutor;

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
    private PriorityMechanisms mechanisms = PriorityMechanisms.full();
    private boolean forceSerialTick;
    private boolean forceParallelTick;
    private SignalNetwork.ControlMode signalControlMode = SignalNetwork.ControlMode.FLOW_GUARD;
    private int worldTick;
    private int mapVersionApplied;
    private final java.util.concurrent.atomic.AtomicInteger corridorSeq = new java.util.concurrent.atomic.AtomicInteger();
    private final VipOps vipOps;
    private final SimExecutor executor = SimExecutor.createDefault();

    private CitySession(SimConfig config, EditableCity city, List<Vehicle> fleet) {
        this.config = Objects.requireNonNull(config, "config");
        this.city = Objects.requireNonNull(city, "city");
        this.mode = SessionMode.BUILD;
        this.fleet = new ArrayList<>(Objects.requireNonNull(fleet, "fleet"));
        this.worldTick = 0;
        this.signals = SignalNetwork.none();
        this.mapVersionApplied = -1;
        this.vipOps = new VipOps(corridors, corridorSeq);
        applyEdits();
        this.mode = SessionMode.BUILD;
    }

    public static CitySession openBlank(SimConfig config) {
        return new CitySession(config, new EditableCity(), List.of());
    }

    /** Kolkata-inspired megacity with named arterials. */
    public static CitySession openKolkata(SimConfig config, int fleetSize, long seed) {
        EditableCity city = OrganicCityGenerator.generateKolkata(seed);
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

    public PriorityMechanisms mechanisms() {
        return mechanisms;
    }

    public void setControlPolicy(ControlPolicy policy) {
        this.controlPolicy = policy == null ? ControlPolicy.CITY_FLOW : policy;
        this.mechanisms = this.controlPolicy.mechanisms();
        if (simulation != null) {
            rebuildSimulation();
        }
    }

    /**
     * Override mechanism flags for ablation without changing the UI policy label.
     * Pass null to reset from {@link #controlPolicy()}.
     */
    public void setMechanisms(PriorityMechanisms mechanisms) {
        this.mechanisms = mechanisms == null ? controlPolicy.mechanisms() : mechanisms;
        if (simulation != null) {
            rebuildSimulation();
        }
    }

    /** When true, ticks run serially (eval / determinism). */
    public void setForceSerialTick(boolean forceSerialTick) {
        this.forceSerialTick = forceSerialTick;
        if (forceSerialTick) {
            this.forceParallelTick = false;
        }
        if (simulation != null) {
            rebuildSimulation();
        }
    }

    /** When true, force parallel tick (tests). Ignored if {@link #setForceSerialTick(boolean)} is on. */
    public void setForceParallelTick(boolean forceParallelTick) {
        this.forceParallelTick = forceParallelTick;
        if (forceParallelTick) {
            this.forceSerialTick = false;
        }
        if (simulation != null) {
            rebuildSimulation();
        }
    }

    public SignalNetwork.ControlMode signalControlMode() {
        return signalControlMode;
    }

    public void setSignalControlMode(SignalNetwork.ControlMode mode) {
        this.signalControlMode = mode == null ? SignalNetwork.ControlMode.FLOW_GUARD : mode;
        if (signals != null && !signals.lights().isEmpty()) {
            this.signals = signals.withMode(this.signalControlMode);
        }
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
        corridors.clear();
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
                    && mechanisms.corridorBlocking()) {
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
        this.signals = GridSignalPlanner.forGraph(fresh, config.lightTiming())
                .withMode(signalControlMode);

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
                mechanisms
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
                traffic, corridors, sc, config.congestionPenaltyPerCar(), mechanisms);
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
        if (sc == ServiceClass.VIP && scheduledDepartAtTick > 0 && mechanisms.corridorBlocking()) {
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
        vipOps.armVipLockdown(traffic, mechanisms, vip, departAt, worldTick, this::replanAroundCorridors);
    }

    /** Replan any non-emergency vehicle whose remaining path hits a hard corridor. */
    public int replanAroundCorridors() {
        return vipOps.replanAroundCorridors(mechanisms, replanner, fleet, traffic, corridors);
    }

    /**
     * Dispatch FIRE / AMBULANCE / POLICE from the nearest matching facility to a scene.
     */
    public Vehicle dispatch(ServiceClass serviceClass, NodeId scene) {
        return EmergencyDispatch.dispatch(
                city,
                traffic.graph(),
                serviceClass,
                scene,
                hasUnappliedEdits(),
                this::applyEdits,
                this::addTrip
        );
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
        boolean parallelTick = !forceSerialTick
                && (forceParallelTick || config.parallelRoutingThreshold() >= 8);
        this.simulation = new Simulation(
                traffic,
                signals,
                fleet,
                config.initialFuel(),
                replanner,
                true,
                config.parallelRoutingThreshold(),
                corridors,
                controlPolicy,
                mechanisms,
                parallelTick,
                executor
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
