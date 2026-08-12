package com.traffic.model.signal;

import com.traffic.model.graph.EdgeId;

import java.util.Objects;
import java.util.Set;

/**
 * A shared resource: cycles GREEN/RED and controls a set of edges.
 * Cars may enter a controlled edge only while this light is GREEN.
 */
public final class TrafficLight {

    private final String name;
    private final Set<EdgeId> controlledEdges;
    private final int greenTicks;
    private final int redTicks;
    private LightColor color;
    private int ticksInPhase;

    public TrafficLight(
            String name,
            Set<EdgeId> controlledEdges,
            int greenTicks,
            int redTicks,
            LightColor initialColor
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.controlledEdges = Set.copyOf(Objects.requireNonNull(controlledEdges, "controlledEdges"));
        if (this.controlledEdges.isEmpty()) {
            throw new IllegalArgumentException("controlledEdges must not be empty");
        }
        if (greenTicks <= 0 || redTicks <= 0) {
            throw new IllegalArgumentException("greenTicks and redTicks must be > 0");
        }
        this.greenTicks = greenTicks;
        this.redTicks = redTicks;
        this.color = Objects.requireNonNull(initialColor, "initialColor");
        this.ticksInPhase = 0;
    }

    public String name() {
        return name;
    }

    public Set<EdgeId> controlledEdges() {
        return controlledEdges;
    }

    public LightColor color() {
        return color;
    }

    public boolean controls(EdgeId edgeId) {
        return controlledEdges.contains(edgeId);
    }

    public boolean allows(EdgeId edgeId) {
        return !controls(edgeId) || color == LightColor.GREEN;
    }

    /** Advance one simulation tick; may flip color when the phase duration elapses. */
    public void tick() {
        ticksInPhase++;
        int limit = color == LightColor.GREEN ? greenTicks : redTicks;
        if (ticksInPhase >= limit) {
            color = color == LightColor.GREEN ? LightColor.RED : LightColor.GREEN;
            ticksInPhase = 0;
        }
    }
}
