package com.traffic.eval;

import com.traffic.model.priority.PriorityMechanisms;

import java.util.List;

/** Named ablation / baseline profiles. Callers: ExperimentRunner, EvalMain, AblationStudyTest. */
public enum MechanismProfile {
    NONE,
    DEPARTURE_ONLY,
    SIGNAL_ONLY,
    CORRIDOR_ONLY,
    SOFT_ROUTING_ONLY,
    FULL,
    FULL_MINUS_DEPARTURE,
    FULL_MINUS_SIGNAL,
    FULL_MINUS_CORRIDOR,
    FULL_MINUS_SOFT;

    public PriorityMechanisms mechanisms() {
        return switch (this) {
            case NONE -> PriorityMechanisms.none();
            case DEPARTURE_ONLY -> PriorityMechanisms.departureOnly();
            case SIGNAL_ONLY -> PriorityMechanisms.signalOnly();
            case CORRIDOR_ONLY -> PriorityMechanisms.corridorOnly();
            case SOFT_ROUTING_ONLY -> PriorityMechanisms.softRoutingOnly();
            case FULL -> PriorityMechanisms.full();
            case FULL_MINUS_DEPARTURE -> PriorityMechanisms.withoutDeparture();
            case FULL_MINUS_SIGNAL -> PriorityMechanisms.withoutSignal();
            case FULL_MINUS_CORRIDOR -> PriorityMechanisms.withoutCorridor();
            case FULL_MINUS_SOFT -> PriorityMechanisms.withoutSoftRouting();
        };
    }

    public static List<MechanismProfile> ablationSet() {
        return List.of(
                NONE,
                DEPARTURE_ONLY,
                SIGNAL_ONLY,
                CORRIDOR_ONLY,
                SOFT_ROUTING_ONLY,
                FULL,
                FULL_MINUS_DEPARTURE,
                FULL_MINUS_SIGNAL,
                FULL_MINUS_CORRIDOR,
                FULL_MINUS_SOFT
        );
    }

    public static List<MechanismProfile> coreCompare() {
        return List.of(NONE, FULL);
    }
}
