package com.traffic.model.signal;

import com.traffic.model.graph.EdgeId;

import java.util.Objects;
import java.util.Set;

/**
 * Shared resource: cycles {@code GREEN → YELLOW → RED → GREEN}.
 * Only GREEN allows new cars to enter controlled edges.
 */
public final class TrafficLight {

    private final String name;
    private final Set<EdgeId> controlledEdges;
    private final LightTiming timing;
    private LightColor color;
    private int ticksInPhase;

    public TrafficLight(
            String name,
            Set<EdgeId> controlledEdges,
            LightTiming timing,
            LightColor initialColor
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.controlledEdges = Set.copyOf(Objects.requireNonNull(controlledEdges, "controlledEdges"));
        if (this.controlledEdges.isEmpty()) {
            throw new IllegalArgumentException("controlledEdges must not be empty");
        }
        this.timing = Objects.requireNonNull(timing, "timing");
        this.color = Objects.requireNonNull(initialColor, "initialColor");
        this.ticksInPhase = 0;
    }

    /** Convenience: green / yellow / red tick counts. */
    public TrafficLight(
            String name,
            Set<EdgeId> controlledEdges,
            int greenTicks,
            int yellowTicks,
            int redTicks,
            LightColor initialColor
    ) {
        this(name, controlledEdges, new LightTiming(greenTicks, yellowTicks, redTicks), initialColor);
    }

    public String name() {
        return name;
    }

    public Set<EdgeId> controlledEdges() {
        return controlledEdges;
    }

    public LightTiming timing() {
        return timing;
    }

    public LightColor color() {
        return color;
    }

    /** Ticks left in the current color (handy for UI progress bars). */
    public int ticksRemainingInPhase() {
        return Math.max(0, timing.duration(color) - ticksInPhase);
    }

    public boolean controls(EdgeId edgeId) {
        return controlledEdges.contains(edgeId);
    }

    public boolean allows(EdgeId edgeId) {
        return !controls(edgeId) || color.allowsNewEntry();
    }

    /** Advance one tick; may change color when the phase ends. */
    public void tick() {
        ticksInPhase++;
        if (ticksInPhase >= timing.duration(color)) {
            color = nextColor(color);
            ticksInPhase = 0;
        }
    }

    private static LightColor nextColor(LightColor current) {
        return switch (current) {
            case GREEN -> LightColor.YELLOW;
            case YELLOW -> LightColor.RED;
            case RED -> LightColor.GREEN;
        };
    }
}
