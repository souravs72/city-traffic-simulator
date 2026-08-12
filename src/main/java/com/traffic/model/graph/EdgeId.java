package com.traffic.model.graph;

/** Stable identity of a one-way road. */

public record EdgeId(int value) {
    public EdgeId {
        if (value < 0) {
            throw new IllegalArgumentException("EdgeId must be >= 0, got " + value);
        }
    }
}
