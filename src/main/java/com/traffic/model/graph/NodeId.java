package com.traffic.model.graph;

/** Stable identity of an intersection. */

public record NodeId(int value) {
    public NodeId {
        if (value < 0) {
            throw new IllegalArgumentException("NodeId must be >= 0, got " + value);
        }
    }
}
