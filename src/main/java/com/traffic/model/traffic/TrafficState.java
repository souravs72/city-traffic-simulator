package com.traffic.model.traffic;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.RoadGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Live occupancy and accidents. Topology stays in {@link RoadGraph}.
 * UI: poll {@link #activeAccidents()} and draw a ✕ where {@link Accident#showCross()}.
 */
public final class TrafficState {

    private final RoadGraph graph;
    private final Map<EdgeId, Integer> occupancy = new HashMap<>();
    private final Map<EdgeId, Accident> accidentsByEdge = new HashMap<>();
    private int accidentSeq;

    public TrafficState(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public RoadGraph graph() {
        return graph;
    }

    public int occupancy(EdgeId edgeId) {
        return occupancy.getOrDefault(edgeId, 0);
    }

    public boolean isClosed(EdgeId edgeId) {
        Accident accident = accidentsByEdge.get(edgeId);
        return accident != null && accident.active();
    }

    /**
     * Spawn a playful incident on a road (blocks new entries until it expires or is cleared).
     * If an accident already exists on that edge, it is replaced.
     */
    public Accident reportAccident(EdgeId edgeId, int durationTicks, String caption) {
        graph.requireEdge(edgeId);
        String id = "accident-" + (++accidentSeq);
        Accident accident = new Accident(id, edgeId, caption, durationTicks);
        accidentsByEdge.put(edgeId, accident);
        return accident;
    }

    /** Clear the ✕ and reopen the road. */
    public void clearAccident(EdgeId edgeId) {
        accidentsByEdge.remove(edgeId);
    }

    /** @deprecated prefer {@link #reportAccident}; kept for short demos */
    @Deprecated
    public void close(EdgeId edgeId) {
        reportAccident(edgeId, 10_000, "Road closed");
    }

    public void reopen(EdgeId edgeId) {
        clearAccident(edgeId);
    }

    public Optional<Accident> accidentOn(EdgeId edgeId) {
        Accident accident = accidentsByEdge.get(edgeId);
        if (accident == null || !accident.active()) {
            return Optional.empty();
        }
        return Optional.of(accident);
    }

    /** Snapshot for UI overlays (✕ + caption). */
    public List<Accident> activeAccidents() {
        List<Accident> active = new ArrayList<>();
        for (Accident accident : accidentsByEdge.values()) {
            if (accident.active()) {
                active.add(accident);
            }
        }
        return List.copyOf(active);
    }

    /** Count down accidents; remove finished ones so the ✕ disappears. */
    public void tickAccidents() {
        List<EdgeId> finished = new ArrayList<>();
        for (var entry : accidentsByEdge.entrySet()) {
            Accident accident = entry.getValue();
            accident.tickDown();
            if (!accident.active()) {
                finished.add(entry.getKey());
            }
        }
        for (EdgeId edgeId : finished) {
            accidentsByEdge.remove(edgeId);
        }
    }

    public boolean hasCapacity(EdgeId edgeId) {
        Edge edge = graph.requireEdge(edgeId);
        return occupancy(edgeId) < edge.capacity();
    }

    public boolean canEnter(EdgeId edgeId) {
        return !isClosed(edgeId) && hasCapacity(edgeId);
    }

    public boolean tryEnter(EdgeId edgeId) {
        if (!canEnter(edgeId)) {
            return false;
        }
        occupancy.merge(edgeId, 1, Integer::sum);
        return true;
    }

    public void leave(EdgeId edgeId) {
        int current = occupancy(edgeId);
        if (current <= 0) {
            throw new IllegalStateException("Cannot leave edge with zero occupancy: " + edgeId);
        }
        if (current == 1) {
            occupancy.remove(edgeId);
        } else {
            occupancy.put(edgeId, current - 1);
        }
    }
}
