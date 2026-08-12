package com.traffic.rules;

import com.traffic.model.graph.Edge;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.routing.EdgeCost;

import java.util.Objects;

/**
 * Class-aware live edge cost.
 * Emergency fleets see a softer congestion penalty.
 * Hard corridors close edges; soft buffers tax lower-priority travelers away.
 */
public final class PriorityEdgeCost implements EdgeCost {

    private static final int CLOSED_COST = 1_000_000;

    private final TrafficState traffic;
    private final CorridorBoard corridors;
    private final ServiceClass traveler;
    private final int penaltyPerCar;
    private final boolean honorPriority;

    public PriorityEdgeCost(
            TrafficState traffic,
            CorridorBoard corridors,
            ServiceClass traveler,
            int penaltyPerCar,
            boolean honorPriority
    ) {
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        this.corridors = Objects.requireNonNull(corridors, "corridors");
        this.traveler = Objects.requireNonNull(traveler, "traveler");
        if (penaltyPerCar < 0) {
            throw new IllegalArgumentException("penaltyPerCar must be >= 0");
        }
        this.penaltyPerCar = penaltyPerCar;
        this.honorPriority = honorPriority;
    }

    public PriorityEdgeCost(
            TrafficState traffic,
            CorridorBoard corridors,
            ServiceClass traveler,
            int penaltyPerCar,
            int worldTick,
            boolean honorPriority
    ) {
        this(traffic, corridors, traveler, penaltyPerCar, honorPriority);
        corridors.setCurrentTick(worldTick);
    }

    @Override
    public int cost(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        if (traffic.isClosed(edge.id())) {
            return CLOSED_COST;
        }
        if (honorPriority && corridors.blocks(edge.id(), traveler)) {
            return CLOSED_COST;
        }
        int occ = traffic.occupancy(edge.id());
        int penalty = penaltyPerCar;
        if (honorPriority && traveler.isEmergency()) {
            // Strong preference for clear emergency paths — still sensitive to total gridlock.
            penalty = traveler.rank() >= ServiceClass.AMBULANCE.rank()
                    ? Math.max(0, penaltyPerCar / 4)
                    : Math.max(0, penaltyPerCar / 2);
        }
        int base = edge.baseWeight();
        if (honorPriority) {
            int soft = corridors.softMultiplier(edge.id(), traveler);
            if (soft > 1) {
                base = base * soft;
            }
        }
        return base + occ * penalty;
    }
}
