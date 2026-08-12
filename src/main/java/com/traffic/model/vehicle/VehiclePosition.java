package com.traffic.model.vehicle;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.NodeId;

import java.util.Objects;

/** Where a vehicle is right now: sitting at an intersection, or traveling on a road. */
public sealed interface VehiclePosition permits VehiclePosition.AtNode, VehiclePosition.OnEdge {

    record AtNode(NodeId node) implements VehiclePosition {
        public AtNode {
            Objects.requireNonNull(node, "node");
        }
    }

    /** {@code ticksRemaining} counts down each sim tick until the car exits the edge. */
    record OnEdge(EdgeId edge, int ticksRemaining) implements VehiclePosition {
        public OnEdge {
            Objects.requireNonNull(edge, "edge");
            if (ticksRemaining < 0) {
                throw new IllegalArgumentException("ticksRemaining must be greater or equal to 0");
            }
        }
    }
}
