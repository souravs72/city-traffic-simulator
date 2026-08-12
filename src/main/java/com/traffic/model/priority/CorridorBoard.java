package com.traffic.model.priority;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.vehicle.ServiceClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Temporary road reservations that divert lower-priority traffic.
 * Hard edges are closed; soft edges are heavily discouraged (cost tax).
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

        /** Hard-only corridor (legacy). */
        public Corridor(String id, ServiceClass minClass, Set<EdgeId> edges, int startTick, int endTick) {
            this(id, minClass, edges, Set.of(), startTick, endTick);
        }

        public boolean activeAt(int tick) {
            return tick >= startTick && tick <= endTick;
        }
    }

    private final List<Corridor> corridors = new ArrayList<>();
    private int currentTick;

    public synchronized void activate(Corridor corridor) {
        corridors.add(Objects.requireNonNull(corridor, "corridor"));
    }

    public synchronized void clear() {
        corridors.clear();
    }

    public synchronized void setCurrentTick(int tick) {
        this.currentTick = tick;
        expireBefore(tick);
    }

    public synchronized int currentTick() {
        return currentTick;
    }

    public synchronized void expireBefore(int tick) {
        corridors.removeIf(c -> tick > c.endTick());
    }

    public synchronized List<Corridor> active(int tick) {
        List<Corridor> out = new ArrayList<>();
        for (Corridor c : corridors) {
            if (c.activeAt(tick)) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    public synchronized boolean blocks(EdgeId edgeId, ServiceClass traveler) {
        return blocks(edgeId, traveler, currentTick);
    }

    public synchronized boolean blocks(EdgeId edgeId, ServiceClass traveler, int tick) {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(traveler, "traveler");
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

    /** Extra travel-cost multiplier for soft buffer roads (1 = none). */
    public synchronized int softMultiplier(EdgeId edgeId, ServiceClass traveler) {
        return softMultiplier(edgeId, traveler, currentTick);
    }

    public synchronized int softMultiplier(EdgeId edgeId, ServiceClass traveler, int tick) {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(traveler, "traveler");
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

    public synchronized Set<EdgeId> blockedEdgesFor(ServiceClass traveler, int tick) {
        Set<EdgeId> blocked = new HashSet<>();
        for (Corridor c : corridors) {
            if (!c.activeAt(tick)) {
                continue;
            }
            if (traveler.rank() < c.minClass().rank()) {
                blocked.addAll(c.edges());
            }
        }
        return Set.copyOf(blocked);
    }

    /** True if any corridor is armed (scheduled or live) and not yet expired. */
    public synchronized boolean hasActive() {
        return corridors.stream().anyMatch(c -> currentTick <= c.endTick());
    }

    /** True if a corridor is inside its live window right now. */
    public synchronized boolean isLive() {
        return corridors.stream().anyMatch(c -> c.activeAt(currentTick));
    }

    /** Hard locks visible in UI: live now, or armed and not expired (preview before VIP departs). */
    public synchronized Set<EdgeId> activeHardEdges() {
        Set<EdgeId> hard = new HashSet<>();
        for (Corridor c : corridors) {
            if (currentTick <= c.endTick()) {
                hard.addAll(c.edges());
            }
        }
        return Set.copyOf(hard);
    }

    public synchronized Set<EdgeId> activeSoftEdges() {
        Set<EdgeId> soft = new HashSet<>();
        Set<EdgeId> hard = activeHardEdges();
        for (Corridor c : corridors) {
            if (currentTick <= c.endTick()) {
                soft.addAll(c.softEdges());
            }
        }
        soft.removeAll(hard);
        return Set.copyOf(soft);
    }

    /** True if any remaining edge on a path is hard-blocked for this class. */
    public synchronized boolean pathBlocked(Iterable<EdgeId> edges, ServiceClass traveler) {
        for (EdgeId id : edges) {
            if (blocks(id, traveler)) {
                return true;
            }
        }
        return false;
    }
}
