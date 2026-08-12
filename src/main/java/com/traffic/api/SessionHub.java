package com.traffic.api;

import com.traffic.api.dto.AccidentRequest;
import com.traffic.api.dto.AddNodeRequest;
import com.traffic.api.dto.BlueprintAccidentDto;
import com.traffic.api.dto.BlueprintEdgeDto;
import com.traffic.api.dto.BlueprintTripDto;
import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.api.dto.PolicyRequest;
import com.traffic.api.dto.FacilityRequest;
import com.traffic.api.dto.DispatchRequest;
import com.traffic.api.dto.VipConvoyRequest;
import com.traffic.api.dto.CompareRequest;
import com.traffic.api.dto.PolicyCompareDto;
import com.traffic.api.dto.IdRequest;
import com.traffic.api.dto.NodeDto;
import com.traffic.api.dto.RushRequest;
import com.traffic.api.dto.SessionSnapshotDto;
import com.traffic.api.dto.TripRequest;
import com.traffic.config.CityGenConfig;
import com.traffic.config.CityPreset;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadType;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.persist.CityStore;
import com.traffic.routing.RoutingAlgorithm;
import com.traffic.rules.AccidentFlavor;
import com.traffic.sim.CitySession;
import com.traffic.sim.SessionMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Single session for the React UI, auto-saved to disk. */
public final class SessionHub {

    private final CityStore store;
    private CitySession session;
    private String activePreset = "BLANK";

    public SessionHub() {
        this(CityStore.defaultStore());
    }

    public SessionHub(CityStore store) {
        this.store = store;
    }

    public synchronized SessionSnapshotDto create(CreateSessionRequest req) {
        boolean hasSave = store.load().isPresent();
        if (hasSave && !req.replaceSaved()) {
            throw new IllegalStateException(
                    "A saved city already exists. Use New city, or confirm replaceSaved to overwrite.");
        }
        boolean namedPreset = req.preset() != null && !req.preset().isBlank();
        CityPreset preset = namedPreset
                ? CityPreset.parse(req.preset())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown preset: " + req.preset()
                                + " (use BLANK, PLAYGROUND, DOWNTOWN, or MEGACITY)"))
                : CityPreset.BLANK;

        int fleet = req.fleetSize() > 0 ? req.fleetSize() : preset.defaultFleetSize();
        int fuel = req.initialFuel() > 0 ? req.initialFuel() : preset.defaultFuel();
        int parallelThreshold = preset.parallelRoutingThreshold();
        int maxTicks = req.maxTicks() > 0 ? req.maxTicks() : preset.defaultMaxTicks();
        long seed = req.seed();

        RoutingAlgorithm algorithm = (preset == CityPreset.MEGACITY)
                ? RoutingAlgorithm.ASTAR
                : RoutingAlgorithm.DIJKSTRA;

        SimConfig config = defaultConfig(maxTicks, fuel, algorithm, parallelThreshold);

        if (!namedPreset && req.rows() > 0 && req.cols() > 0) {
            CityGenConfig gen = new CityGenConfig(req.rows(), req.cols(), 80.0, 3, true);
            this.session = CitySession.openGrid(config, gen, Math.max(fleet, req.fleetSize()), seed);
            this.activePreset = "CUSTOM";
        } else if (preset.isBlank()) {
            this.session = CitySession.openBlank(config);
            this.activePreset = "BLANK";
        } else if (preset == CityPreset.MEGACITY) {
            this.session = CitySession.openOrganic(config, fleet, seed);
            this.activePreset = "MEGACITY";
        } else {
            this.session = CitySession.openGrid(config, preset.toGenConfig(), fleet, seed);
            this.activePreset = preset.name();
        }
        persist();
        return SnapshotMapper.from(session);
    }

    /** Wipe save and start a fresh blank canvas. */
    public synchronized SessionSnapshotDto newCity() {
        store.clear();
        return create(new CreateSessionRequest("BLANK", 0, 0, 0, 42L, 0, 300));
    }

    public synchronized SessionSnapshotDto snapshot() {
        ensureSession();
        return SnapshotMapper.from(requireSession());
    }

    public synchronized boolean hasSession() {
        ensureSession();
        return session != null;
    }

    public synchronized CityBlueprintDto exportBlueprint() {
        ensureSession();
        return toBlueprint(requireSession(), activePreset);
    }

    public synchronized SessionSnapshotDto restore(CityBlueprintDto blueprint) {
        this.session = fromBlueprint(blueprint);
        this.activePreset = blueprint.preset() == null ? "BLANK" : blueprint.preset();
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto build() {
        requireSession().build();
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto play() {
        requireSession().play();
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto apply() {
        requireSession().applyEdits();
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto step() {
        requireSession().step();
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto run(int ticks) {
        CitySession s = requireSession();
        int n = ticks > 0 ? ticks : 10;
        s.run(n);
        persist();
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto addNode(AddNodeRequest req) {
        CitySession s = requireSession();
        if (s.mode() != SessionMode.BUILD) {
            throw new IllegalStateException("Add nodes only in BUILD mode — pause the clock first");
        }
        s.addNode(req.x(), req.y(), req.label());
        persist();
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto removeNode(IdRequest req) {
        CitySession s = requireSession();
        if (s.mode() != SessionMode.BUILD) {
            throw new IllegalStateException("Delete nodes only in BUILD mode — pause the clock first");
        }
        s.removeNode(new NodeId(req.id()));
        persist();
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto connect(ConnectEdgeRequest req) {
        CitySession s = requireSession();
        if (s.mode() != SessionMode.BUILD) {
            throw new IllegalStateException("Connect edges only in BUILD mode — pause the clock first");
        }
        RoadType type = RoadType.parse(req.roadType());
        int weight = type.travelTicks();
        int cap = req.capacity() > 0 ? req.capacity() : type.capacity();
        NodeId from = new NodeId(req.from());
        NodeId to = new NodeId(req.to());
        if (req.twoWay()) {
            s.city().connectOneWay(from, to, weight, cap);
            s.city().connectOneWay(to, from, weight, cap);
        } else {
            s.city().connectOneWay(from, to, weight, cap);
        }
        persist();
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto removeEdge(IdRequest req) {
        CitySession s = requireSession();
        if (s.mode() != SessionMode.BUILD) {
            throw new IllegalStateException("Delete roads only in BUILD mode — pause the clock first");
        }
        s.removeRoad(new EdgeId(req.id()));
        persist();
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto accident(AccidentRequest req) {
        CitySession s = requireSession();
        String caption = req.caption() == null || req.caption().isBlank()
                ? AccidentFlavor.randomCaption()
                : req.caption();
        int duration = req.durationTicks() > 0 ? req.durationTicks() : s.config().accidentDurationTicks();
        s.traffic().reportAccident(new EdgeId(req.edgeId()), duration, caption);
        persist();
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto setFacility(FacilityRequest req) {
        requireSession();
        session.setFacility(new NodeId(req.nodeId()), FacilityKind.parse(req.facility()));
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto setPolicy(PolicyRequest req) {
        requireSession();
        session.setControlPolicy(ControlPolicy.valueOf(
                req.policy() == null ? "CITY_FLOW" : req.policy().trim().toUpperCase()));
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto dispatch(DispatchRequest req) {
        requireSession();
        session.dispatch(ServiceClass.parse(req.serviceClass()), new NodeId(req.sceneNodeId()));
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto vipConvoy(VipConvoyRequest req) {
        requireSession();
        int escorts = req.escorts() > 0 ? req.escorts() : 2;
        session.scheduleVipConvoy(
                new NodeId(req.from()),
                new NodeId(req.to()),
                req.departAtTick(),
                escorts
        );
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized PolicyCompareDto compare(CompareRequest req) {
        requireSession();
        int ticks = req == null || req.ticks() <= 0 ? 80 : req.ticks();
        // Snapshot blueprint without mutating live session policy permanently.
        return com.traffic.sim.PolicyArena.compare(toBlueprint(session, activePreset), ticks);
    }

    public synchronized SessionSnapshotDto seedFacilities() {
        requireSession();
        session.ensureFacilities(42L);
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto addTrip(TripRequest req) {
        requireSession();
        String name = (req.name() == null || req.name().isBlank()) ? null : req.name().trim();
        session.addTrip(
                new NodeId(req.from()),
                new NodeId(req.to()),
                name,
                ServiceClass.parse(req.serviceClass()),
                req.scheduledDepartAtTick()
        );
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto addRandomTrip() {
        requireSession().addRandomTrip();
        persist();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto addRushHour(RushRequest req) {
        CitySession s = requireSession();
        int count = req == null || req.count() <= 0 ? 8 : Math.min(40, req.count());
        s.addRushHour(count);
        persist();
        return SnapshotMapper.from(s);
    }

    private void ensureSession() {
        if (session != null) {
            return;
        }
        store.load().ifPresent(bp -> {
            try {
                this.session = fromBlueprint(bp);
                this.activePreset = bp.preset() == null ? "BLANK" : bp.preset();
                System.out.println("Restored city from " + store.path());
            } catch (Exception ex) {
                System.err.println("Saved city unreadable: " + ex.getMessage());
            }
        });
    }

    private void persist() {
        if (session == null) {
            return;
        }
        store.save(toBlueprint(session, activePreset));
    }

    private CitySession requireSession() {
        ensureSession();
        if (session == null) {
            throw new IllegalStateException("No session — POST /api/session first");
        }
        return session;
    }

    private static SimConfig defaultConfig(
            int maxTicks,
            int fuel,
            RoutingAlgorithm algorithm,
            int parallelThreshold
    ) {
        return new SimConfig(
                maxTicks,
                fuel,
                algorithm,
                LightTiming.playful(),
                2,
                8,
                false,
                parallelThreshold
        );
    }

    static CityBlueprintDto toBlueprint(CitySession session, String preset) {
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
            trips.add(new BlueprintTripDto(v.origin().value(), v.destination().value(), v.name(), v.serviceClass().name(), v.scheduledDepartAtTick()));
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
                accidents
        );
    }

    public static CitySession sessionFromBlueprint(CityBlueprintDto bp) {
        return fromBlueprint(bp);
    }

    static CitySession fromBlueprint(CityBlueprintDto bp) {
        SimConfig config = defaultConfig(300, 120, RoutingAlgorithm.DIJKSTRA, 8);
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
                    // skip trips that no longer have a path
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
