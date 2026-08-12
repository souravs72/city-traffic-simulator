package com.traffic.model.priority;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.vehicle.ServiceClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Temporary road reservations that divert lower-priority traffic.
 * Writers refresh an immutable {@link Snapshot}; readers (routing) are lock-free.
 */
public final class CorridorBoard {

    public record Corridor(
            String id,
            ServiceClass minClass,
            Set<EdgeId> edges,
            Set<EdgeId> softEdges,
            int startTick,
            int endTick
    ) {
        public Corridor {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(minClass, "minClass");
            edges = Set.copyOf(Objects.requireNonNull(edges, "edges"));
            softEdges = softEdges == null ? Set.of() : Set.copyOf(softEdges);
            if (edges.isEmpty() && softEdges.isEmpty()) {
                throw new IllegalArgumentException("corridor must reserve at least one edge");
            }
            if (endTick < startTick) {
                throw new IllegalArgumentException("endTick must be >= startTick");
            }
        }

        public Corridor(String id, ServiceClass minClass, Set<EdgeId> edges, int startTick, int endTick) {
            this(id, minClass, edges, Set.of(), startTick, endTick);
        }

        public boolean activeAt(int tick) {
            return tick >= startTick && tick <= endTick;
        }
    }

    public record Snapshot(int currentTick, List<Corridor> corridors) {
        public Snapshot {
            corridors = List.copyOf(corridors);
        }

        public boolean blocks(EdgeId edgeId, ServiceClass traveler) {
            return blocks(edgeId, traveler, currentTick);
        }

        public boolean blocks(EdgeId edgeId, ServiceClass traveler, int tick) {
            for (Corridor c : corridors) {
                if (!c.activeAt(tick)) {
                    continue;
                }
                if (c.edges().contains(edgeId) && traveler.rank() < c.minClass().rank()) {
                    return true;
                }
            }
            return false;
        }

        public int softMultiplier(EdgeId edgeId, ServiceClass traveler) {
            return softMultiplier(edgeId, traveler, currentTick);
        }

        public int softMultiplier(EdgeId edgeId, ServiceClass traveler, int tick) {
            int mult = 1;
            for (Corridor c : corridors) {
                if (!c.activeAt(tick)) {
                    continue;
                }
                if (traveler.rank() < c.minClass().rank() && c.softEdges().contains(edgeId)) {
                    mult = Math.max(mult, 3);
                }
            }
            return mult;
        }
    }

    private final ReentrantLock writeLock = new ReentrantLock();
    private final List<Corridor> corridors = new ArrayList<>();
    private volatile Snapshot snapshot = new Snapshot(0, List.of());

    public void activate(Corridor corridor) {
        writeLock.lock();
        try {
            corridors.add(Objects.requireNonNull(corridor, "corridor"));
            publish();
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            corridors.clear();
            publish();
        } finally {
            writeLock.unlock();
        }
    }

    public void setCurrentTick(int tick) {
        writeLock.lock();
        try {
            corridors.removeIf(c -> tick > c.endTick());
            publish(tick);
        } finally {
            writeLock.unlock();
        }
    }

    public int currentTick() {
        return snapshot.currentTick();
    }

    public void expireBefore(int tick) {
        writeLock.lock();
        try {
            corridors.removeIf(c -> tick > c.endTick());
            publish(tick);
        } finally {
            writeLock.unlock();
        }
    }

    public List<Corridor> active(int tick) {
        List<Corridor> out = new ArrayList<>();
        for (Corridor c : snapshot.corridors()) {
            if (c.activeAt(tick)) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public boolean blocks(EdgeId edgeId, ServiceClass traveler) {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(traveler, "traveler");
        return snapshot.blocks(edgeId, traveler);
    }

    public boolean blocks(EdgeId edgeId, ServiceClass traveler, int tick) {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(traveler, "traveler");
        return snapshot.blocks(edgeId, traveler, tick);
    }

    public int softMultiplier(EdgeId edgeId, ServiceClass traveler) {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(traveler, "traveler");
        return snapshot.softMultiplier(edgeId, traveler);
    }

    public int softMultiplier(EdgeId edgeId, ServiceClass traveler, int tick) {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(traveler, "traveler");
        return snapshot.softMultiplier(edgeId, traveler, tick);
    }

    public Set<EdgeId> blockedEdgesFor(ServiceClass traveler, int tick) {
        Set<EdgeId> blocked = new HashSet<>();
        for (Corridor c : snapshot.corridors()) {
            if (!c.activeAt(tick)) {
                continue;
            }
            if (traveler.rank() < c.minClass().rank()) {
                blocked.addAll(c.edges());
            }
        }
        return Set.copyOf(blocked);
    }

    public boolean hasActive() {
        int tick = snapshot.currentTick();
        return snapshot.corridors().stream().anyMatch(c -> tick <= c.endTick());
    }

    public boolean isLive() {
        int tick = snapshot.currentTick();
        return snapshot.corridors().stream().anyMatch(c -> c.activeAt(tick));
    }

    public Set<EdgeId> activeHardEdges() {
        int tick = snapshot.currentTick();
        Set<EdgeId> hard = new HashSet<>();
        for (Corridor c : snapshot.corridors()) {
            if (tick <= c.endTick()) {
                hard.addAll(c.edges());
            }
        }
        return Set.copyOf(hard);
    }

    public Set<EdgeId> activeSoftEdges() {
        int tick = snapshot.currentTick();
        Set<EdgeId> soft = new HashSet<>();
        Set<EdgeId> hard = activeHardEdges();
        for (Corridor c : snapshot.corridors()) {
            if (tick <= c.endTick()) {
                soft.addAll(c.softEdges());
            }
        }
        soft.removeAll(hard);
        return Set.copyOf(soft);
    }

    public boolean pathBlocked(Iterable<EdgeId> edges, ServiceClass traveler) {
        Snapshot snap = snapshot;
        for (EdgeId id : edges) {
            if (snap.blocks(id, traveler)) {
                return true;
            }
        }
        return false;
    }

    private void publish() {
        publish(snapshot.currentTick());
    }

    private void publish(int tick) {
        snapshot = new Snapshot(tick, List.copyOf(corridors));
    }
}
