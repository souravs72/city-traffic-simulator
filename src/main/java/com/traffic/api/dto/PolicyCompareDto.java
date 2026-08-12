package com.traffic.api.dto;

/**
 * Strict A/B compare DTO with p90 + Jain fairness fields.
 */
public record PolicyCompareDto(
        int ticks,
        int fleetSize,
        PolicyLegDto mapsLike,
        PolicyLegDto cityFlow,
        boolean cityFlowWinsEmergency,
        boolean cityFlowCivilianFair,
        String verdict,
        double mapsFleetP90,
        double cityFlowFleetP90,
        double mapsJainCivilian,
        double cityFlowJainCivilian,
        double cityFlowEmergencyP90,
        double mapsEmergencyP90
) {
    public record PolicyLegDto(
            String policy,
            int emergencyArrivalTicks,
            double civilianAvgTicks,
            double fleetAvgTicks,
            int arrived,
            int stranded,
            double fleetP90,
            double civilianP90,
            double emergencyP90,
            double jainCivilian
    ) {
    }
}
