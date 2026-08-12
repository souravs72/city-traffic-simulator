package com.traffic.blueprint;

import com.traffic.api.dto.BlueprintAccidentDto;
import com.traffic.api.dto.BlueprintEdgeDto;
import com.traffic.api.dto.BlueprintTripDto;
import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.api.dto.NodeDto;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadType;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.routing.RoutingAlgorithm;
import com.traffic.sim.CitySession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain blueprint encode/decode. Callers: eval ExperimentRunner, api BlueprintMapper.
 * Schema: CityBlueprintDto. User: fix review findings to high standard.
 */
public final class CityBlueprints {

    public static final int SCHEMA_VERSION = 1;

    private CityBlueprints() {
    }

    public static CityBlueprintDto snapshot(CitySession session, String preset) {
        List<NodeDto> nodes = new ArrayList<>();
        for (Node node : session.city().nodes()) {
            nodes.add(new NodeDto(node.id().value(), node.label(), node.x(), node.y(), node.facility().name()));
        }
        nodes.sort(Comparator.comparingInt(NodeDto::id));

        List<BlueprintEdgeDto> edges = new ArrayList<>();
        for (Edge edge : session.city().edges()) {
            RoadType type = RoadType.classify(edge.baseWeight(), edge.capacity());
            edges.add(new BlueprintEdgeDto(
                    edge.from().value(),
                    edge.to().value(),
                    edge.baseWeight(),
                    edge.capacity(),
                    type.name()
            ));
        }

        List<BlueprintTripDto> trips = new ArrayList<>();
        for (Vehicle v : session.fleet()) {
            trips.add(new BlueprintTripDto(
                    v.origin().value(),
                    v.destination().value(),
                    v.name(),
                    v.serviceClass().name(),
                    v.scheduledDepartAtTick()));
        }

        List<BlueprintAccidentDto> accidents = new ArrayList<>();
        for (var accident : session.traffic().activeAccidents()) {
            var edge = session.city().edge(accident.edgeId()).orElse(null);
            if (edge == null) {
                continue;
            }
            accidents.add(new BlueprintAccidentDto(
                    edge.from().value(),
                    edge.to().value(),
                    accident.ticksRemaining(),
                    accident.caption()
            ));
        }

        return new CityBlueprintDto(
                preset == null ? "BLANK" : preset,
                nodes,
                edges,
                trips,
                accidents,
                SCHEMA_VERSION
        );
    }

    public static CitySession restore(CityBlueprintDto bp) {
        SimConfig config = new SimConfig(
                300,
                120,
                RoutingAlgorithm.DIJKSTRA,
                LightTiming.playful(),
                2,
                8,
                false,
                0
        );
        CitySession session = CitySession.openBlank(config);
        Map<Integer, NodeId> remap = new HashMap<>();

        List<NodeDto> nodes = new ArrayList<>(bp.nodes() == null ? List.of() : bp.nodes());
        nodes.sort(Comparator.comparingInt(NodeDto::id));
        for (NodeDto node : nodes) {
            FacilityKind facility = FacilityKind.parse(node.facility());
            Node created = session.addNode(node.x(), node.y(), node.label(), facility);
            remap.put(node.id(), created.id());
        }

        if (bp.edges() != null) {
            for (BlueprintEdgeDto edge : bp.edges()) {
                NodeId from = remap.get(edge.from());
                NodeId to = remap.get(edge.to());
                if (from == null || to == null) {
                    continue;
                }
                int weight = edge.baseWeight() > 0
                        ? edge.baseWeight()
                        : RoadType.parse(edge.roadType()).travelTicks();
                int cap = edge.capacity() > 0
                        ? edge.capacity()
                        : RoadType.parse(edge.roadType()).capacity();
                if (session.city().findEdge(from, to).isEmpty()) {
                    session.city().addEdgeExplicit(from, to, weight, cap);
                }
            }
        }

        session.applyEdits();

        if (bp.trips() != null) {
            for (BlueprintTripDto trip : bp.trips()) {
                NodeId from = remap.get(trip.from());
                NodeId to = remap.get(trip.to());
                if (from == null || to == null || from.equals(to)) {
                    continue;
                }
                String name = trip.name() == null || trip.name().isBlank() ? "Car" : trip.name();
                ServiceClass sc = ServiceClass.parse(trip.serviceClass());
                int depart = trip.scheduledDepartAtTick();
                try {
                    session.addTrip(from, to, name, sc, depart);
                } catch (RuntimeException ignored) {
                    // skip unreachable OD
                }
            }
        }

        if (bp.accidents() != null) {
            for (BlueprintAccidentDto accident : bp.accidents()) {
                NodeId from = remap.get(accident.from());
                NodeId to = remap.get(accident.to());
                if (from == null || to == null) {
                    continue;
                }
                session.city().findEdge(from, to).ifPresent(edge -> {
                    int duration = accident.durationTicks() > 0 ? accident.durationTicks() : 20;
                    session.traffic().reportAccident(
                            edge.id(),
                            duration,
                            accident.caption() == null ? "Road closed" : accident.caption()
                    );
                });
            }
        }

        session.build();
        return session;
    }
}
