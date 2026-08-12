package com.traffic.sim;

import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Routers;
import com.traffic.rules.DynamicEdgeCost;
import com.traffic.rules.Replanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private int worldTick;
    private int mapVersionApplied;

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

    public static CitySession openGrid(SimConfig config, CityGenConfig genConfig, int fleetSize, long seed) {
        EditableCity city = GridCityGenerator.generate(genConfig);
        RoadGraph graph = city.snapshot();
        TrafficState bootstrap = new TrafficState(graph);
        EdgeCost cost = new DynamicEdgeCost(bootstrap, config.congestionPenaltyPerCar());
        List<FleetFactory.Trip> trips = FleetFactory.randomTrips(graph, fleetSize, seed);
        List<Vehicle> fleet = FleetFactory.spawn(graph, config, cost, trips);
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

    public int worldTick() {
        return worldTick;
    }

    public void build() {
        mode = SessionMode.BUILD;
    }

    /** Switch to play; applies pending map edits first. */
    public void play() {
        applyEdits();
        mode = SessionMode.PLAY;
    }

    /**
     * Snapshot {@link EditableCity} → new {@link RoadGraph}, migrate cars off deleted roads,
     * replan everyone. Safe to call from Build or before Play.
     */
    public int applyEdits() {
        evacuateFleetToNodes();

        RoadGraph fresh = city.snapshot();
        this.traffic = new TrafficState(fresh);
        EdgeCost cost = new DynamicEdgeCost(traffic, config.congestionPenaltyPerCar());
        this.replanner = new Replanner(Routers.create(config.routingAlgorithm(), fresh), cost);
        this.signals = SignalNetwork.none();

        int replans = 0;
        for (Vehicle vehicle : fleet) {
            if (vehicle.arrived()) {
                continue;
            }
            int before = vehicle.replanCount();
            if (replanner.replan(vehicle, fresh) && vehicle.replanCount() > before) {
                replans++;
            }
        }

        this.simulation = new Simulation(
                traffic,
                signals,
                fleet,
                config.initialFuel(),
                replanner
        );
        this.mapVersionApplied = city.version();
        return replans;
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
