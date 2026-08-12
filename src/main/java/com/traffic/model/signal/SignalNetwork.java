package com.traffic.model.signal;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.traffic.TrafficState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * FlowGuard signal network — decongestion-first control.
 * <p>
 * Principles (proven in {@code SignalImpactBenchmarkTest}):
 * <ol>
 *   <li>Never deny a car when the opposing approach has nothing to serve</li>
 *   <li>Prefer the approach with highest <em>effective pressure</em> (waiters × age − spillback)</li>
 *   <li>Do not waste green feeding a road that is already at capacity</li>
 *   <li>Bound starvation so a quiet-but-waiting approach eventually gets served</li>
 * </ol>
 */
public final class SignalNetwork {

    public enum ControlMode {
        /** Live decongestion policy. */
        FLOW_GUARD,
        /** Classic timed phases — benchmark baseline. */
        FIXED_CYCLE
    }

    public record Pair(TrafficLight a, TrafficLight b) {
        public Pair {
            Objects.requireNonNull(a, "a");
            Objects.requireNonNull(b, "b");
        }
    }

    private final List<TrafficLight> lights;
    private final List<Pair> pairs;
    private final Map<EdgeId, TrafficLight> byEdge;
    private final ControlMode mode;
    /** Last privileged ranks seen on approaches (VIP+). */
    private Map<EdgeId, Integer> lastPriorityByEdge = Map.of();

    public SignalNetwork(List<TrafficLight> lights) {
        this(lights, List.of(), ControlMode.FLOW_GUARD);
    }

    public SignalNetwork(List<TrafficLight> lights, List<Pair> pairs) {
        this(lights, pairs, ControlMode.FLOW_GUARD);
    }

    public SignalNetwork(List<TrafficLight> lights, List<Pair> pairs, ControlMode mode) {
        this.lights = List.copyOf(Objects.requireNonNull(lights, "lights"));
        this.pairs = List.copyOf(Objects.requireNonNull(pairs, "pairs"));
        this.mode = Objects.requireNonNull(mode, "mode");
        Map<EdgeId, TrafficLight> index = new HashMap<>();
        for (TrafficLight light : this.lights) {
            for (EdgeId edgeId : light.controlledEdges()) {
                TrafficLight previous = index.put(edgeId, light);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Edge " + edgeId + " controlled by both "
                                    + previous.name() + " and " + light.name());
                }
            }
        }
        this.byEdge = Map.copyOf(index);
    }

    public static SignalNetwork none() {
        return new SignalNetwork(List.of());
    }

    public List<TrafficLight> lights() {
        return lights;
    }

    public List<Pair> pairs() {
        return pairs;
    }

    public boolean isOpen(EdgeId edgeId) {
        Objects.requireNonNull(edgeId, "edgeId");
        TrafficLight light = byEdge.get(edgeId);
        return light == null || light.allows(edgeId);
    }

    public Optional<LightColor> colorOf(EdgeId edgeId) {
        Objects.requireNonNull(edgeId, "edgeId");
        TrafficLight light = byEdge.get(edgeId);
        return light == null ? Optional.empty() : Optional.of(light.color());
    }

    /** Fixed-cycle tick (benchmark baseline / unit tests). */
    public void tick() {
        for (TrafficLight light : lights) {
            light.tick();
        }
    }

    /** FlowGuard tick — default live policy. */
    public void tick(TrafficState traffic, Map<EdgeId, Integer> waitingByEdge) {
        tick(traffic, waitingByEdge, Map.of());
    }

    public void tick(
            TrafficState traffic,
            Map<EdgeId, Integer> waitingByEdge,
            Map<EdgeId, Integer> waitAgeByEdge
    ) {
        tick(traffic, waitingByEdge, waitAgeByEdge, Map.of());
    }

    /**
     * @param priorityByEdge max {@link com.traffic.model.vehicle.ServiceClass#rank()} waiting on approach
     */
    public void tick(
            TrafficState traffic,
            Map<EdgeId, Integer> waitingByEdge,
            Map<EdgeId, Integer> waitAgeByEdge,
            Map<EdgeId, Integer> priorityByEdge
    ) {
        if (mode == ControlMode.FIXED_CYCLE) {
            tick();
            return;
        }
        Objects.requireNonNull(traffic, "traffic");
        Map<EdgeId, Integer> waiting = waitingByEdge == null ? Map.of() : waitingByEdge;
        Map<EdgeId, Integer> ages = waitAgeByEdge == null ? Map.of() : waitAgeByEdge;
        Map<EdgeId, Integer> pri = priorityByEdge == null ? Map.of() : Map.copyOf(priorityByEdge);
        this.lastPriorityByEdge = pri;
        Set<TrafficLight> paired = new HashSet<>();
        for (Pair pair : pairs) {
            int pa = maxPriority(pair.a(), pri);
            int pb = maxPriority(pair.b(), pri);
            // VIP+ privilege: hard-cut green unless a higher-rank unit needs the other approach.
            // Equal ranks: brief arbitration by pressure (stopping then benefits the system).
            if (pa >= 1 || pb >= 1) {
                if (pa > pb) {
                    servePriorityCut(pair.a(), pair.b());
                } else if (pb > pa) {
                    servePriorityCut(pair.b(), pair.a());
                } else if (pa > 0) {
                    int da = effectivePressure(pair.a(), traffic, waiting, ages);
                    int db = effectivePressure(pair.b(), traffic, waiting, ages);
                    if (da >= db) {
                        servePriorityCut(pair.a(), pair.b());
                    } else {
                        servePriorityCut(pair.b(), pair.a());
                    }
                }
                paired.add(pair.a());
                paired.add(pair.b());
                continue;
            }
            int da = effectivePressure(pair.a(), traffic, waiting, ages);
            int db = effectivePressure(pair.b(), traffic, waiting, ages);
            controlPairFlowGuard(pair.a(), pair.b(), da, db, traffic, waiting);
            paired.add(pair.a());
            paired.add(pair.b());
        }
        for (TrafficLight light : lights) {
            if (!paired.contains(light)) {
                light.forceGreen();
            }
        }
    }

    /**
     * Hard cut for privileged traffic — skip yellow delay so emergency/VIP do not sit.
     * Safe in this model because occupancy is on edges, not an intersection box.
     */
    private static void servePriorityCut(TrafficLight serve, TrafficLight stop) {
        stop.forceRed();
        if (serve.color() == LightColor.GREEN) {
            serve.holdGreen();
        } else {
            serve.forceGreen();
        }
    }

    /**
     * Privileged entry check: VIP/emergency may proceed on red only when no higher-rank
     * demand is waiting on a conflicting approach (i.e. stopping would not help the system).
     */
    public boolean allowsEntry(EdgeId edgeId, int travelerRank) {
        Objects.requireNonNull(edgeId, "edgeId");
        if (isOpen(edgeId)) {
            return true;
        }
        if (mode == ControlMode.FIXED_CYCLE) {
            return false;
        }
        if (travelerRank < 1) { // below VIP
            return false;
        }
        TrafficLight mine = byEdge.get(edgeId);
        if (mine == null) {
            return true;
        }
        for (Pair pair : pairs) {
            TrafficLight other = null;
            if (pair.a() == mine) {
                other = pair.b();
            } else if (pair.b() == mine) {
                other = pair.a();
            }
            if (other == null) {
                continue;
            }
            int otherPri = maxPriority(other, lastPriorityByEdge);
            // Cut through only when we strictly outrank opposing demand.
            // Equal/higher opposing → obey the light (stopping helps the system).
            return otherPri < travelerRank;
        }
        // Unpaired light: privileged traffic never waits.
        return true;
    }

    private static int maxPriority(TrafficLight light, Map<EdgeId, Integer> priorityByEdge) {
        int max = 0;
        for (EdgeId edgeId : light.controlledEdges()) {
            max = Math.max(max, priorityByEdge.getOrDefault(edgeId, 0));
        }
        return max;
    }

    /**
     * Effective pressure: queue size + cumulative wait age − spillback tax.
     * Spillback tax removes pressure when every controlled exit is jammed/closed,
     * so we hand green to the other approach instead of wasting the phase.
     */
    static int effectivePressure(
            TrafficLight light,
            TrafficState traffic,
            Map<EdgeId, Integer> waiting,
            Map<EdgeId, Integer> ages
    ) {
        int waiters = 0;
        int age = 0;
        boolean anyAccepting = false;
        boolean anyWaiter = false;
        for (EdgeId edgeId : light.controlledEdges()) {
            int w = waiting.getOrDefault(edgeId, 0);
            waiters += w;
            age += ages.getOrDefault(edgeId, 0);
            if (w > 0) {
                anyWaiter = true;
            }
            if (traffic.canEnter(edgeId)) {
                anyAccepting = true;
            }
        }
        int pressure = waiters * 10 + age + trafficOccupancyBoost(light, traffic);
        if (anyWaiter && !anyAccepting) {
            // Feeding a saturated approach wastes green — collapse pressure.
            return 0;
        }
        return pressure;
    }

    private static int trafficOccupancyBoost(TrafficLight light, TrafficState traffic) {
        int boost = 0;
        for (EdgeId edgeId : light.controlledEdges()) {
            boost += traffic.occupancy(edgeId);
        }
        return boost;
    }

    static void controlPairFlowGuard(
            TrafficLight a,
            TrafficLight b,
            int da,
            int db,
            TrafficState traffic,
            Map<EdgeId, Integer> waiting
    ) {
        if (a.color() == LightColor.YELLOW) {
            boolean done = a.advanceClearance();
            b.forceRed();
            if (done) {
                grantAfterClearance(a, b, da, db);
            }
            return;
        }
        if (b.color() == LightColor.YELLOW) {
            boolean done = b.advanceClearance();
            a.forceRed();
            if (done) {
                grantAfterClearance(b, a, db, da);
            }
            return;
        }

        // Conflict-free: never block the only side that needs the junction.
        if (da == 0 && db == 0) {
            keepIdleGreen(a, b);
            return;
        }
        if (da > 0 && db == 0) {
            serveExclusive(a, b);
            return;
        }
        if (db > 0 && da == 0) {
            serveExclusive(b, a);
            return;
        }

        // Both want service — max pressure with hysteresis, starvation bound, spillback awareness.
        // Hysteresis stops phase thrash when pressures are nearly equal (wastes yellow clearance).
        final int handoffMargin = 12;
        if (a.color() == LightColor.GREEN) {
            boolean starveB = db > 0 && b.ticksDenied() >= b.starvationTicks();
            boolean aWasted = !approachCanAccept(a, traffic, waiting) && approachCanAccept(b, traffic, waiting);
            if (a.ticksInPhase() < a.minGreenTicks() && !aWasted) {
                a.holdGreen();
                b.forceRed();
            } else if (starveB || db > da + handoffMargin || aWasted) {
                a.beginYellow();
                b.forceRed();
            } else {
                a.holdGreen();
                b.forceRed();
            }
            return;
        }
        if (b.color() == LightColor.GREEN) {
            boolean starveA = da > 0 && a.ticksDenied() >= a.starvationTicks();
            boolean bWasted = !approachCanAccept(b, traffic, waiting) && approachCanAccept(a, traffic, waiting);
            if (b.ticksInPhase() < b.minGreenTicks() && !bWasted) {
                b.holdGreen();
                a.forceRed();
            } else if (starveA || da > db + handoffMargin || bWasted) {
                b.beginYellow();
                a.forceRed();
            } else {
                b.holdGreen();
                a.forceRed();
            }
            return;
        }

        if (da >= db) {
            a.forceGreen();
            b.forceRed();
        } else {
            b.forceGreen();
            a.forceRed();
        }
    }

    /** Legacy pressure API used by older unit tests. */
    static void controlPair(TrafficLight a, TrafficLight b, int da, int db) {
        controlPairFlowGuard(a, b, da, db, null, Map.of());
    }

    private static boolean approachCanAccept(
            TrafficLight light,
            TrafficState traffic,
            Map<EdgeId, Integer> waiting
    ) {
        if (traffic == null) {
            return true;
        }
        for (EdgeId edgeId : light.controlledEdges()) {
            if (waiting.getOrDefault(edgeId, 0) > 0 && traffic.canEnter(edgeId)) {
                return true;
            }
            if (waiting.getOrDefault(edgeId, 0) == 0 && traffic.canEnter(edgeId)) {
                // still useful if platoon is on the approach edges
                Edge edge = traffic.graph().requireEdge(edgeId);
                if (traffic.occupancy(edgeId) < edge.capacity()) {
                    return true;
                }
            }
        }
        // If nobody waiting, "accepting" doesn't matter for waste detection.
        boolean anyWait = false;
        for (EdgeId edgeId : light.controlledEdges()) {
            if (waiting.getOrDefault(edgeId, 0) > 0) {
                anyWait = true;
                break;
            }
        }
        return !anyWait;
    }

    private static void keepIdleGreen(TrafficLight a, TrafficLight b) {
        if (a.color() == LightColor.GREEN) {
            a.holdGreen();
            b.forceRed();
        } else if (b.color() == LightColor.GREEN) {
            b.holdGreen();
            a.forceRed();
        } else {
            a.forceGreen();
            b.forceRed();
        }
    }

    private static void serveExclusive(TrafficLight serve, TrafficLight stop) {
        if (serve.color() == LightColor.GREEN) {
            serve.holdGreen();
            stop.forceRed();
            return;
        }
        if (stop.color() == LightColor.GREEN) {
            stop.beginYellow();
            return;
        }
        serve.forceGreen();
        stop.forceRed();
    }

    private static void grantAfterClearance(TrafficLight cleared, TrafficLight other, int dCleared, int dOther) {
        if (dOther > 0 && dOther >= dCleared) {
            other.forceGreen();
            cleared.forceRed();
        } else if (dCleared > 0) {
            cleared.forceGreen();
            other.forceRed();
        } else if (dOther > 0) {
            other.forceGreen();
            cleared.forceRed();
        } else {
            cleared.forceGreen();
            other.forceRed();
        }
    }
}
