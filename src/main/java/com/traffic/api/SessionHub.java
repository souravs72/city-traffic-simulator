package com.traffic.api;

import com.traffic.api.dto.AccidentRequest;
import com.traffic.api.dto.AddNodeRequest;
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
import com.traffic.api.dto.RushRequest;
import com.traffic.api.dto.SessionSnapshotDto;
import com.traffic.api.dto.TripRequest;
import com.traffic.config.CityGenConfig;
import com.traffic.config.CityPreset;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadType;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.persist.CityStore;
import com.traffic.routing.RoutingAlgorithm;
import com.traffic.rules.AccidentFlavor;
import com.traffic.sim.CitySession;
import com.traffic.sim.PolicyArena;
import com.traffic.sim.SessionMode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-session hub: each browser/API client binds an id ({@code X-Session-Id}).
 * Persist runs after releasing the per-session lock.
 */
public final class SessionHub {

    public static final String DEFAULT_SESSION = "default";

    private final ApiConfig limits;
    private final Path dataDir;
    private final ConcurrentHashMap<String, Slot> sessions = new ConcurrentHashMap<>();
    private final ThreadLocal<String> boundId = ThreadLocal.withInitial(() -> DEFAULT_SESSION);

    public SessionHub() {
        this(CityStore.defaultStore(), ApiConfig.localLab(8080, Path.of("data")));
    }

    public SessionHub(CityStore store) {
        this(store, ApiConfig.localLab(8080, store.path().getParent() == null
                ? Path.of("data")
                : store.path().getParent()));
    }

    public SessionHub(CityStore store, ApiConfig limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.dataDir = limits.dataDir();
        // Seed default slot with provided store path for backward-compatible tests.
        sessions.put(DEFAULT_SESSION, new Slot(DEFAULT_SESSION, store));
    }

    public void bind(String sessionId) {
        String id = (sessionId == null || sessionId.isBlank()) ? DEFAULT_SESSION : sessionId.trim();
        boundId.set(id);
        sessions.computeIfAbsent(id, this::newSlot);
    }

    public String boundSessionId() {
        return boundId.get();
    }

    public int sessionCount() {
        return sessions.size();
    }

    private Slot newSlot(String id) {
        Path file = dataDir.resolve("city-flow-" + sanitize(id) + ".json");
        return new Slot(id, new CityStore(file));
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Slot slot() {
        return sessions.computeIfAbsent(boundId.get(), this::newSlot);
    }

    public Map<String, Object> healthDetails() {
        Slot s = slot();
        synchronized (s) {
            s.ensureSession();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("hasSession", s.session != null);
            details.put("sessionId", s.id);
            details.put("sessionCount", sessions.size());
            if (s.session != null) {
                details.put("preset", s.activePreset);
                details.put("sessionMode", s.session.mode().name());
                details.put("worldTick", s.session.worldTick());
                details.put("fleetSize", s.session.fleet().size());
                details.put("nodeCount", s.session.city().nodes().size());
                details.put("parallelTick", s.session.simulation() != null && s.session.simulation().parallelTick());
            }
            details.put("savePath", s.store.path().toString());
            return details;
        }
    }

    public SessionSnapshotDto create(CreateSessionRequest req) {
        Slot s = slot();
        CityBlueprintDto toSave;
        SessionSnapshotDto snap;
        synchronized (s) {
            snap = s.create(req, limits);
            toSave = s.blueprintOrNull();
        }
        persistOutside(s, toSave);
        return snap;
    }

    public SessionSnapshotDto newCity() {
        Slot s = slot();
        CityBlueprintDto toSave;
        SessionSnapshotDto snap;
        synchronized (s) {
            s.store.clear();
            snap = s.create(new CreateSessionRequest("PLAYGROUND", 0, 0, 0, 42L, 0, 300, true), limits);
            toSave = s.blueprintOrNull();
        }
        persistOutside(s, toSave);
        return snap;
    }

    public SessionSnapshotDto snapshot() {
        synchronized (slot()) {
            return SnapshotMapper.from(slot().requireSession());
        }
    }

    public boolean hasSession() {
        synchronized (slot()) {
            slot().ensureSession();
            return slot().session != null;
        }
    }

    public CityBlueprintDto exportBlueprint() {
        synchronized (slot()) {
            Slot s = slot();
            return BlueprintMapper.toBlueprint(s.requireSession(), s.activePreset);
        }
    }

    public SessionSnapshotDto restore(CityBlueprintDto blueprint) {
        Slot s = slot();
        CityBlueprintDto toSave;
        SessionSnapshotDto snap;
        synchronized (s) {
            s.session = BlueprintMapper.fromBlueprint(blueprint);
            s.activePreset = blueprint.preset() == null ? "BLANK" : blueprint.preset();
            snap = SnapshotMapper.from(s.session);
            toSave = s.blueprintOrNull();
        }
        persistOutside(s, toSave);
        return snap;
    }

    public SessionSnapshotDto build() { return mutate(CitySession::build); }
    public SessionSnapshotDto play() { return mutate(CitySession::play); }
    public SessionSnapshotDto apply() { return mutate(CitySession::applyEdits); }
    public SessionSnapshotDto step() { return mutate(CitySession::step); }

    public SessionSnapshotDto run(int ticks) {
        Slot s = slot();
        CityBlueprintDto toSave;
        SessionSnapshotDto snap;
        synchronized (s) {
            CitySession cs = s.requireSession();
            cs.run(ticks > 0 ? ticks : 10);
            snap = SnapshotMapper.from(cs);
            toSave = s.blueprintOrNull();
        }
        persistOutside(s, toSave);
        return snap;
    }

    public SessionSnapshotDto addNode(AddNodeRequest req) {
        return mutate(cs -> {
            if (cs.mode() != SessionMode.BUILD) {
                throw new IllegalStateException("Add nodes only in BUILD mode — pause the clock first");
            }
            cs.addNode(req.x(), req.y(), req.label());
        });
    }

    public SessionSnapshotDto removeNode(IdRequest req) {
        return mutate(cs -> {
            if (cs.mode() != SessionMode.BUILD) {
                throw new IllegalStateException("Delete nodes only in BUILD mode — pause the clock first");
            }
            cs.removeNode(new NodeId(req.id()));
        });
    }

    public SessionSnapshotDto connect(ConnectEdgeRequest req) {
        return mutate(cs -> {
            if (cs.mode() != SessionMode.BUILD) {
                throw new IllegalStateException("Connect edges only in BUILD mode — pause the clock first");
            }
            RoadType type = RoadType.parse(req.roadType());
            int weight = type.travelTicks();
            int cap = req.capacity() > 0 ? req.capacity() : type.capacity();
            NodeId from = new NodeId(req.from());
            NodeId to = new NodeId(req.to());
            if (req.twoWay()) {
                cs.city().connectOneWay(from, to, weight, cap);
                cs.city().connectOneWay(to, from, weight, cap);
            } else {
                cs.city().connectOneWay(from, to, weight, cap);
            }
        });
    }

    public SessionSnapshotDto removeEdge(IdRequest req) {
        return mutate(cs -> {
            if (cs.mode() != SessionMode.BUILD) {
                throw new IllegalStateException("Delete roads only in BUILD mode — pause the clock first");
            }
            cs.removeRoad(new EdgeId(req.id()));
        });
    }

    public SessionSnapshotDto accident(AccidentRequest req) {
        return mutate(cs -> {
            String caption = req.caption() == null || req.caption().isBlank()
                    ? AccidentFlavor.randomCaption()
                    : req.caption();
            int duration = req.durationTicks() > 0 ? req.durationTicks() : cs.config().accidentDurationTicks();
            cs.traffic().reportAccident(new EdgeId(req.edgeId()), duration, caption);
        });
    }

    public SessionSnapshotDto setFacility(FacilityRequest req) {
        return mutate(cs -> cs.setFacility(new NodeId(req.nodeId()), FacilityKind.parse(req.facility())));
    }

    public SessionSnapshotDto setPolicy(PolicyRequest req) {
        return mutate(cs -> cs.setControlPolicy(ControlPolicy.valueOf(
                req.policy() == null ? "CITY_FLOW" : req.policy().trim().toUpperCase())));
    }

    public SessionSnapshotDto dispatch(DispatchRequest req) {
        return mutate(cs -> cs.dispatch(ServiceClass.parse(req.serviceClass()), new NodeId(req.sceneNodeId())));
    }

    public SessionSnapshotDto vipConvoy(VipConvoyRequest req) {
        return mutate(cs -> {
            int escorts = req.escorts() > 0 ? req.escorts() : 2;
            cs.scheduleVipConvoy(new NodeId(req.from()), new NodeId(req.to()), req.departAtTick(), escorts);
        });
    }

    public PolicyCompareDto compare(CompareRequest req) {
        synchronized (slot()) {
            Slot s = slot();
            CitySession cs = s.requireSession();
            int ticks = req == null || req.ticks() <= 0 ? 80 : req.ticks();
            return PolicyArena.compare(BlueprintMapper.toBlueprint(cs, s.activePreset), ticks);
        }
    }

    public SessionSnapshotDto seedFacilities() {
        return mutate(cs -> cs.ensureFacilities(42L));
    }

    public SessionSnapshotDto addTrip(TripRequest req) {
        return mutate(cs -> {
            String name = (req.name() == null || req.name().isBlank()) ? null : req.name().trim();
            cs.addTrip(
                    new NodeId(req.from()),
                    new NodeId(req.to()),
                    name,
                    ServiceClass.parse(req.serviceClass()),
                    req.scheduledDepartAtTick()
            );
        });
    }

    public SessionSnapshotDto addRandomTrip() {
        return mutate(CitySession::addRandomTrip);
    }

    public SessionSnapshotDto addRushHour(RushRequest req) {
        return mutate(cs -> {
            int requested = req == null || req.count() <= 0 ? 8 : req.count();
            cs.addRushHour(limits.clampRush(requested));
        });
    }

    /** Allocate a fresh isolated session id (for multi-tab demos). */
    public String allocateSessionId() {
        String id = "s-" + UUID.randomUUID().toString().substring(0, 8);
        sessions.computeIfAbsent(id, this::newSlot);
        return id;
    }

    private SessionSnapshotDto mutate(SessionOp op) {
        Slot s = slot();
        CityBlueprintDto toSave;
        SessionSnapshotDto snap;
        synchronized (s) {
            CitySession cs = s.requireSession();
            op.apply(cs);
            snap = SnapshotMapper.from(cs);
            toSave = s.blueprintOrNull();
        }
        persistOutside(s, toSave);
        return snap;
    }

    private void persistOutside(Slot s, CityBlueprintDto blueprint) {
        if (blueprint != null) {
            s.store.save(blueprint);
        }
    }

    @FunctionalInterface
    private interface SessionOp {
        void apply(CitySession session);
    }

    private final class Slot {
        private final String id;
        private final CityStore store;
        private CitySession session;
        private String activePreset = "BLANK";

        private Slot(String id, CityStore store) {
            this.id = id;
            this.store = store;
        }

        private CityBlueprintDto blueprintOrNull() {
            if (session == null) {
                return null;
            }
            return BlueprintMapper.toBlueprint(session, activePreset);
        }

        private SessionSnapshotDto create(CreateSessionRequest req, ApiConfig limits) {
            boolean hasSave = store.load().isPresent();
            if (hasSave && !req.replaceSaved()) {
                throw new IllegalStateException(
                        "A saved city already exists. Use New city, or confirm replaceSaved to overwrite.");
            }
            limits.validateCreate(req.rows(), req.cols(), req.fleetSize());
            boolean namedPreset = req.preset() != null && !req.preset().isBlank();
            CityPreset preset = namedPreset
                    ? CityPreset.parse(req.preset())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown preset: " + req.preset()
                                    + " (use BLANK, PLAYGROUND, DOWNTOWN, MEGACITY, or KOLKATA)"))
                    : CityPreset.BLANK;

            int fleet = req.fleetSize() > 0 ? req.fleetSize() : preset.defaultFleetSize();
            if (fleet > limits.maxFleet()) {
                throw new IllegalArgumentException(
                        "Fleet too large — max " + limits.maxFleet() + " (got " + fleet + ")");
            }
            int fuel = req.initialFuel() > 0 ? req.initialFuel() : preset.defaultFuel();
            int parallelThreshold = preset.parallelRoutingThreshold();
            int maxTicks = req.maxTicks() > 0 ? req.maxTicks() : preset.defaultMaxTicks();
            long seed = req.seed();

            RoutingAlgorithm algorithm = (preset == CityPreset.MEGACITY || preset == CityPreset.KOLKATA)
                    ? RoutingAlgorithm.ASTAR
                    : RoutingAlgorithm.DIJKSTRA;

            SimConfig config = new SimConfig(
                    maxTicks, fuel, algorithm, LightTiming.playful(), 2, 8, false, parallelThreshold);

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
            } else if (preset == CityPreset.KOLKATA) {
                this.session = CitySession.openKolkata(config, fleet, seed);
                this.activePreset = "KOLKATA";
            } else {
                this.session = CitySession.openGrid(config, preset.toGenConfig(), fleet, seed);
                this.activePreset = preset.name();
            }
            return SnapshotMapper.from(session);
        }

        private void ensureSession() {
            if (session != null) {
                return;
            }
            store.load().ifPresent(bp -> {
                try {
                    this.session = BlueprintMapper.fromBlueprint(bp);
                    this.activePreset = bp.preset() == null ? "BLANK" : bp.preset();
                    System.out.println("Restored city [" + id + "] from " + store.path());
                } catch (Exception ex) {
                    System.err.println("Saved city unreadable: " + ex.getMessage());
                }
            });
        }

        private CitySession requireSession() {
            ensureSession();
            if (session == null) {
                throw new IllegalStateException("No session — POST /api/session first");
            }
            return session;
        }
    }

    /** @deprecated use {@link BlueprintMapper#fromBlueprint} */
    public static CitySession sessionFromBlueprint(CityBlueprintDto bp) {
        return BlueprintMapper.fromBlueprint(bp);
    }
}
