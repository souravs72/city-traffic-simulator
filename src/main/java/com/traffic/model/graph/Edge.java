package com.traffic.model.graph;

import java.util.Objects;


/** A directed raod. Live occupancy does not live here */


public record Edge(EdgeId id, NodeId from, NodeId to, int baseWeight, int capacity) { 
    public Edge { 
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (baseWeight <= 0) {
            throw new IllegalArgumentException("baseWeight must be greather than 0");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greather than 0");
        }
    }
}