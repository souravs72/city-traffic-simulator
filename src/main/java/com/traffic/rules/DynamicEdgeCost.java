package com.traffic.rules;

import com.traffic.model.graph.Edge;
import com.traffic.model.traffic.TrafficState;
import com.traffic.routing.EdgeCost;

import java.util.Objects;

/**
 * Dynamic travel cost: baseWeight + congestion penalty per car on the edge.
 * Closed edges get a huge cost so routers avoid them.
 */
public final class DynamicEdgeCost implements EdgeCost {

    private static final int CLOSED_COST = 1_000_000;

    private final TrafficState traffic;
    private final int penaltyPerCar;

    public DynamicEdgeCost(TrafficState traffic, int penaltyPerCar) {
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        if (penaltyPerCar < 0) {
            throw new IllegalArgumentException("penaltyPerCar must be >= 0");
        }
        this.penaltyPerCar = penaltyPerCar;
    }

    @Override
    public int cost(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        if (traffic.isClosed(edge.id())) {
            return CLOSED_COST;
        }
        int occ = traffic.occupancy(edge.id());
        return edge.baseWeight() + occ * penaltyPerCar;
    }
}
