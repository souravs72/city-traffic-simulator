package com.traffic.eval;

import com.traffic.api.dto.BlueprintEdgeDto;
import com.traffic.api.dto.BlueprintTripDto;
import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.api.dto.NodeDto;
import com.traffic.blueprint.CityBlueprints;
import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.graph.RoadType;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.sim.CitySession;
import com.traffic.sim.FleetFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deterministic, seed-aware scenario blueprints for eval / ablation.
 */
public final class ScenarioFixtures {

    private ScenarioFixtures() {
    }

    /** Seed 0 canonical corridor (stable canary for unit tests). */
    public static CityBlueprintDto emergencyCorridor() {
        return emergencyCorridor(0L);
    }

    /**
     * Fixed topology; seed reshuffles which civilian OD pairs run (ambulance fixed).
     * Distinct seeds yield distinct demand mixes → meaningful multi-seed variance.
     */
    public static CityBlueprintDto emergencyCorridor(long seed) {
        List<NodeDto> nodes = baseNodes();
        List<BlueprintEdgeDto> edges = baseEdges();

        int[][] civilianPool = {
                {1, 3}, {5, 3}, {2, 0}, {4, 0}, {1, 0}, {2, 3}, {5, 0}, {4, 3}
        };
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < civilianPool.length; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(seed));

        List<BlueprintTripDto> trips = new ArrayList<>();
        trips.add(trip(0, 3, "Ambulance-1", ServiceClass.AMBULANCE.name(), 0));
        for (int i = 0; i < 4; i++) {
            int[] od = civilianPool[order.get(i)];
            trips.add(trip(od[0], od[1], "Civ-" + (i + 1), ServiceClass.CIVILIAN.name(), 0));
        }
        return new CityBlueprintDto("EVAL_EMERGENCY", nodes, edges, trips, List.of(), 1);
    }

    public static CityBlueprintDto vipPlusEmergency() {
        return vipPlusEmergency(0L);
    }

    public static CityBlueprintDto vipPlusEmergency(long seed) {
        CityBlueprintDto base = emergencyCorridor(seed);
        List<BlueprintTripDto> trips = new ArrayList<>(base.trips());
        int depart = 6 + (int) Math.floorMod(seed, 5L);
        trips.add(trip(0, 3, "VIP-1", ServiceClass.VIP.name(), depart));
        return new CityBlueprintDto(base.preset(), base.nodes(), base.edges(), trips, base.accidents(), 1);
    }

    public static CityBlueprintDto playgroundDemand(long seed, int fleetSize) {
        SimConfig config = SimConfig.defaults().withParallelRoutingThreshold(0);
        CitySession session = CitySession.openGrid(config, CityGenConfig.playground(), 0, seed);
        session.ensureFacilities(seed);
        RoadGraph graph = session.city().snapshot();
        List<FleetFactory.Trip> trips = FleetFactory.commuteTrips(graph, Math.max(1, fleetSize), seed);
        for (FleetFactory.Trip t : trips) {
            try {
                session.addTrip(t.start(), t.goal(), t.nickname());
            } catch (RuntimeException ignored) {
                // skip blocked pairs
            }
        }
        injectAmbulance(session);
        return CityBlueprints.snapshot(session, "PLAYGROUND");
    }

    public static CityBlueprintDto downtownDemand(long seed, int fleetSize) {
        SimConfig config = SimConfig.defaults().withParallelRoutingThreshold(0);
        CitySession session = CitySession.openGrid(config, CityGenConfig.downtown(), 0, seed);
        session.ensureFacilities(seed);
        RoadGraph graph = session.city().snapshot();
        List<FleetFactory.Trip> trips = FleetFactory.randomTrips(graph, Math.max(1, fleetSize), seed);
        for (FleetFactory.Trip t : trips) {
            try {
                session.addTrip(t.start(), t.goal(), t.nickname());
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        injectAmbulance(session);
        return CityBlueprints.snapshot(session, "DOWNTOWN");
    }

    private static void injectAmbulance(CitySession session) {
        Node hospital = null;
        Node far = null;
        for (Node n : session.city().nodes()) {
            if (n.facility() == FacilityKind.HOSPITAL) {
                hospital = n;
            }
            far = n;
        }
        if (hospital != null && far != null && !hospital.id().equals(far.id())) {
            try {
                session.addTrip(hospital.id(), far.id(), "Eval-Ambulance", ServiceClass.AMBULANCE, 0);
            } catch (RuntimeException ignored) {
                // optional
            }
        }
    }

    private static List<NodeDto> baseNodes() {
        return List.of(
                new NodeDto(0, "A", 0, 0, FacilityKind.HOSPITAL.name()),
                new NodeDto(1, "B", 100, 0, FacilityKind.NONE.name()),
                new NodeDto(2, "C", 200, 0, FacilityKind.NONE.name()),
                new NodeDto(3, "D", 300, 0, FacilityKind.NONE.name()),
                new NodeDto(4, "E", 200, 100, FacilityKind.NONE.name()),
                new NodeDto(5, "F", 100, 100, FacilityKind.NONE.name())
        );
    }

    private static List<BlueprintEdgeDto> baseEdges() {
        return List.of(
                e(0, 1, 4, 2), e(1, 0, 4, 2),
                e(1, 2, 4, 2), e(2, 1, 4, 2),
                e(2, 3, 4, 2), e(3, 2, 4, 2),
                e(1, 5, 3, 1), e(5, 1, 3, 1),
                e(5, 4, 3, 1), e(4, 5, 3, 1),
                e(4, 2, 3, 1), e(2, 4, 3, 1),
                e(4, 3, 4, 1), e(3, 4, 4, 1)
        );
    }

    private static BlueprintEdgeDto e(int from, int to, int weight, int capacity) {
        return new BlueprintEdgeDto(from, to, weight, capacity, RoadType.AVENUE.name());
    }

    private static BlueprintTripDto trip(
            int from, int to, String name, String serviceClass, int depart
    ) {
        return new BlueprintTripDto(from, to, name, serviceClass, depart);
    }
}
