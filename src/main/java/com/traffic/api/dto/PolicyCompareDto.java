package com.traffic.api.dto;

/**
 * Strict A/B: same city + fleet recipe under MAPS_LIKE vs CITY_FLOW.
 * Lower emergency/civilian averages are better.
 */
public record PolicyCompareDto(
        int ticks,
        int fleetSize,
        PolicyLegDto mapsLike,
        PolicyLegDto cityFlow,
        boolean cityFlowWinsEmergency,
        boolean cityFlowCivilianFair,
        String verdict
) {
    public record PolicyLegDto(
            String policy,
            int emergencyArrivalTicks,
            double civilianAvgTicks,
            double fleetAvgTicks,
            int arrived,
            int stranded
    ) {
    }
}
