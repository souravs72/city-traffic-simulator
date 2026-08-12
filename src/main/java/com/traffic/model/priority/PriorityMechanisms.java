package com.traffic.model.priority;

import java.util.Objects;

/**
 * Granular priority stack for ablation studies.
 * {@link ControlPolicy#CITY_FLOW} maps to {@link #full()}; {@link ControlPolicy#MAPS_LIKE} to {@link #none()}.
 */
public record PriorityMechanisms(
        boolean priorityDeparture,
        boolean signalPreemption,
        boolean corridorBlocking,
        boolean softBufferRouting
) {
    public static PriorityMechanisms none() {
        return new PriorityMechanisms(false, false, false, false);
    }

    public static PriorityMechanisms full() {
        return new PriorityMechanisms(true, true, true, true);
    }

    public static PriorityMechanisms from(ControlPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return policy.honorPriority() ? full() : none();
    }

    public static PriorityMechanisms departureOnly() {
        return new PriorityMechanisms(true, false, false, false);
    }

    public static PriorityMechanisms signalOnly() {
        return new PriorityMechanisms(false, true, false, false);
    }

    public static PriorityMechanisms corridorOnly() {
        return new PriorityMechanisms(false, false, true, false);
    }

    public static PriorityMechanisms softRoutingOnly() {
        return new PriorityMechanisms(false, false, false, true);
    }

    public static PriorityMechanisms withoutDeparture() {
        return full().withPriorityDeparture(false);
    }

    public static PriorityMechanisms withoutSignal() {
        return full().withSignalPreemption(false);
    }

    public static PriorityMechanisms withoutCorridor() {
        return full().withCorridorBlocking(false);
    }

    public static PriorityMechanisms withoutSoftRouting() {
        return full().withSoftBufferRouting(false);
    }

    public PriorityMechanisms withPriorityDeparture(boolean v) {
        return new PriorityMechanisms(v, signalPreemption, corridorBlocking, softBufferRouting);
    }

    public PriorityMechanisms withSignalPreemption(boolean v) {
        return new PriorityMechanisms(priorityDeparture, v, corridorBlocking, softBufferRouting);
    }

    public PriorityMechanisms withCorridorBlocking(boolean v) {
        return new PriorityMechanisms(priorityDeparture, signalPreemption, v, softBufferRouting);
    }

    public PriorityMechanisms withSoftBufferRouting(boolean v) {
        return new PriorityMechanisms(priorityDeparture, signalPreemption, corridorBlocking, v);
    }

    public boolean any() {
        return priorityDeparture || signalPreemption || corridorBlocking || softBufferRouting;
    }

    public String profileName() {
        if (!any()) {
            return "NONE";
        }
        if (equals(full())) {
            return "FULL";
        }
        if (equals(departureOnly())) {
            return "DEPARTURE_ONLY";
        }
        if (equals(signalOnly())) {
            return "SIGNAL_ONLY";
        }
        if (equals(corridorOnly())) {
            return "CORRIDOR_ONLY";
        }
        if (equals(softRoutingOnly())) {
            return "SOFT_ROUTING_ONLY";
        }
        if (equals(withoutDeparture())) {
            return "FULL_MINUS_DEPARTURE";
        }
        if (equals(withoutSignal())) {
            return "FULL_MINUS_SIGNAL";
        }
        if (equals(withoutCorridor())) {
            return "FULL_MINUS_CORRIDOR";
        }
        if (equals(withoutSoftRouting())) {
            return "FULL_MINUS_SOFT";
        }
        return "CUSTOM_"
                + (priorityDeparture ? "D" : "")
                + (signalPreemption ? "S" : "")
                + (corridorBlocking ? "C" : "")
                + (softBufferRouting ? "R" : "");
    }
}
