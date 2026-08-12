package com.traffic.model.traffic;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.RoadGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Live occupancy and accidents. Topology stays in {@link RoadGraph}.
 * Per-edge lock striping keeps {@link #tryEnter}/{@link #leave} correct under
 * concurrent readers (pathfinding) and writers (fleet departures).
 * UI: poll {@link #activeAccidents()} and draw a ✕ where {@link Accident#showCross()}.
 */
public final class TrafficState {

    private static final int STRIPES = 64;

    private final RoadGraph graph;
    private final ConcurrentHashMap<EdgeId, Integer> occupancy = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EdgeId, Accident> accidentsByEdge = new ConcurrentHashMap<>();
    private final ReentrantLock[] edgeLocks;
    private final AtomicInteger accidentSeq = new AtomicInteger();

    public TrafficState(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.edgeLocks = new ReentrantLock[STRIPES];
        for (int i = 0; i < STRIPES; i++) {
            edgeLocks[i] = new ReentrantLock();
        }
    }

    public static int stripeCount() {
        return STRIPES;
    }

    public int stripeIndex(EdgeId edgeId) {
        return Math.floorMod(edgeId.value(), STRIPES);
    }

    private ReentrantLock lockFor(EdgeId edgeId) {
        return edgeLocks[stripeIndex(edgeId)];
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
        ReentrantLock lock = lockFor(edgeId);
        lock.lock();
        try {
            String id = "accident-" + accidentSeq.incrementAndGet();
            Accident accident = new Accident(id, edgeId, caption, durationTicks);
            accidentsByEdge.put(edgeId, accident);
            return accident;
        } finally {
            lock.unlock();
        }
    }

    /** Clear the ✕ and reopen the road. */
    public void clearAccident(EdgeId edgeId) {
        ReentrantLock lock = lockFor(edgeId);
        lock.lock();
        try {
            accidentsByEdge.remove(edgeId);
        } finally {
            lock.unlock();
        }
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
        for (EdgeId edgeId : List.copyOf(accidentsByEdge.keySet())) {
            ReentrantLock lock = lockFor(edgeId);
            lock.lock();
            try {
                Accident accident = accidentsByEdge.get(edgeId);
                if (accident == null) {
                    continue;
                }
                accident.tickDown();
                if (!accident.active()) {
                    accidentsByEdge.remove(edgeId);
                }
            } finally {
                lock.unlock();
            }
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
        ReentrantLock lock = lockFor(edgeId);
        lock.lock();
        try {
            if (!canEnter(edgeId)) {
                return false;
            }
            occupancy.merge(edgeId, 1, Integer::sum);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void leave(EdgeId edgeId) {
        ReentrantLock lock = lockFor(edgeId);
        lock.lock();
        try {
            int current = occupancy(edgeId);
            if (current <= 0) {
                throw new IllegalStateException("Cannot leave edge with zero occupancy: " + edgeId);
            }
            if (current == 1) {
                occupancy.remove(edgeId);
            } else {
                occupancy.put(edgeId, current - 1);
            }
        } finally {
            lock.unlock();
        }
    }
}
