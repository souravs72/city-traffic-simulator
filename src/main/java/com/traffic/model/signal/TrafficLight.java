package com.traffic.model.signal;

import com.traffic.model.graph.EdgeId;

import java.util.Objects;
import java.util.Set;

/**
 * Shared resource at an intersection approach.
 * Live sims use {@link SignalNetwork} FlowGuard control — not blind fixed cycles.
 */
public final class TrafficLight {

    private final String name;
    private final Set<EdgeId> controlledEdges;
    private final LightTiming timing;
    private LightColor color;
    private int ticksInPhase;
    /** Continuous ticks spent not green (red/yellow) — used for starvation bounds. */
    private int ticksDenied;

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
        this.ticksDenied = initialColor == LightColor.GREEN ? 0 : 1;
    }

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

    public int ticksInPhase() {
        return ticksInPhase;
    }

    public int ticksDenied() {
        return ticksDenied;
    }

    public int ticksRemainingInPhase() {
        return Math.max(0, timing.duration(color) - ticksInPhase);
    }

    public boolean controls(EdgeId edgeId) {
        return controlledEdges.contains(edgeId);
    }

    public boolean allows(EdgeId edgeId) {
        return !controls(edgeId) || color.allowsNewEntry();
    }

    public void tick() {
        ticksInPhase++;
        if (color != LightColor.GREEN) {
            ticksDenied++;
        }
        if (ticksInPhase >= timing.duration(color)) {
            color = nextColor(color);
            ticksInPhase = 0;
            if (color == LightColor.GREEN) {
                ticksDenied = 0;
            }
        }
    }

    public void forceGreen() {
        color = LightColor.GREEN;
        ticksInPhase = 0;
        ticksDenied = 0;
    }

    public void forceRed() {
        if (color == LightColor.GREEN) {
            ticksDenied = 0;
        }
        color = LightColor.RED;
        ticksInPhase = 0;
        ticksDenied++;
    }

    public void beginYellow() {
        color = LightColor.YELLOW;
        ticksInPhase = 0;
        ticksDenied++;
    }

    public void holdGreen() {
        if (color != LightColor.GREEN) {
            forceGreen();
            return;
        }
        ticksInPhase++;
        ticksDenied = 0;
    }

    public boolean advanceClearance() {
        if (color != LightColor.YELLOW) {
            return color == LightColor.RED;
        }
        ticksInPhase++;
        ticksDenied++;
        if (ticksInPhase >= timing.yellowTicks()) {
            color = LightColor.RED;
            ticksInPhase = 0;
            return true;
        }
        return false;
    }

    public int minGreenTicks() {
        return Math.max(1, Math.min(2, timing.greenTicks()));
    }

    /** Max time an approach with demand may stay denied before a forced handoff. */
    public int starvationTicks() {
        return Math.max(6, timing.greenTicks() * 2 + timing.yellowTicks());
    }

    private static LightColor nextColor(LightColor current) {
        return switch (current) {
            case GREEN -> LightColor.YELLOW;
            case YELLOW -> LightColor.RED;
            case RED -> LightColor.GREEN;
        };
    }
}
