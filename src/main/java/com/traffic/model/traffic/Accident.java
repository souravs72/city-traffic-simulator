package com.traffic.model.traffic;

import com.traffic.model.graph.EdgeId;

import java.util.Objects;

/**
 * A temporary road incident. UI can draw a ✕ on {@link #edgeId()} while {@link #active()}.
 */
public final class Accident {

    private final String id;
    private final EdgeId edgeId;
    private final String caption;
    private int ticksRemaining;

    public Accident(String id, EdgeId edgeId, String caption, int durationTicks) {
        this.id = Objects.requireNonNull(id, "id");
        this.edgeId = Objects.requireNonNull(edgeId, "edgeId");
        this.caption = Objects.requireNonNull(caption, "caption");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be > 0");
        }
        this.ticksRemaining = durationTicks;
    }

    public String id() {
        return id;
    }

    public EdgeId edgeId() {
        return edgeId;
    }

    /** Short playful text for tooltips / banners. */
    public String caption() {
        return caption;
    }

    /** Hint for UI: show a cross / ✕ overlay on this road. */
    public boolean showCross() {
        return active();
    }

    public int ticksRemaining() {
        return ticksRemaining;
    }

    public boolean active() {
        return ticksRemaining > 0;
    }

    /** One sim tick closer to cleanup. */
    void tickDown() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }
}
