package com.traffic.rules;

import com.traffic.model.graph.Edge;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.priority.PriorityMechanisms;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.routing.EdgeCost;

import java.util.Objects;

/**
 * Class-aware live edge cost.
 * Hard corridors close edges; soft buffers tax lower-priority travelers away.
 * Mechanism flags enable ablation of corridor vs soft-routing contributions.
 */
public final class PriorityEdgeCost implements EdgeCost {

    private static final int CLOSED_COST = EdgeCost.CLOSED;

    private final TrafficState traffic;
    private final CorridorBoard corridors;
    private final ServiceClass traveler;
    private final int penaltyPerCar;
    private final PriorityMechanisms mechanisms;

    public PriorityEdgeCost(
            TrafficState traffic,
            CorridorBoard corridors,
            ServiceClass traveler,
            int penaltyPerCar,
            PriorityMechanisms mechanisms
    ) {
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        this.corridors = Objects.requireNonNull(corridors, "corridors");
        this.traveler = Objects.requireNonNull(traveler, "traveler");
        if (penaltyPerCar < 0) {
            throw new IllegalArgumentException("penaltyPerCar must be >= 0");
        }
        this.penaltyPerCar = penaltyPerCar;
        this.mechanisms = mechanisms == null ? PriorityMechanisms.none() : mechanisms;
    }

    public PriorityEdgeCost(
            TrafficState traffic,
            CorridorBoard corridors,
            ServiceClass traveler,
            int penaltyPerCar,
            int worldTick,
            PriorityMechanisms mechanisms
    ) {
        this(traffic, corridors, traveler, penaltyPerCar, mechanisms);
        corridors.setCurrentTick(worldTick);
    }

    /** @deprecated Prefer {@link PriorityMechanisms}; retained for call-site migration. */
    @Deprecated
    public PriorityEdgeCost(
            TrafficState traffic,
            CorridorBoard corridors,
            ServiceClass traveler,
            int penaltyPerCar,
            boolean honorPriority
    ) {
        this(traffic, corridors, traveler, penaltyPerCar,
                honorPriority ? PriorityMechanisms.full() : PriorityMechanisms.none());
    }

    @Override
    public int cost(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        if (traffic.isClosed(edge.id())) {
            return CLOSED_COST;
        }
        if (mechanisms.corridorBlocking() && corridors.blocks(edge.id(), traveler)) {
            return CLOSED_COST;
        }
        int occ = traffic.occupancy(edge.id());
        int penalty = penaltyPerCar;
        if (mechanisms.softBufferRouting() && traveler.isEmergency()) {
            penalty = traveler.rank() >= ServiceClass.AMBULANCE.rank()
                    ? Math.max(0, penaltyPerCar / 4)
                    : Math.max(0, penaltyPerCar / 2);
        }
        int base = edge.baseWeight();
        if (mechanisms.softBufferRouting()) {
            int soft = corridors.softMultiplier(edge.id(), traveler);
            if (soft > 1) {
                base = base * soft;
            }
        }
        return base + occ * penalty;
    }
}
