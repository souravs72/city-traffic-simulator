package com.traffic.eval;

/**
 * Paper-facing metrics for one simulation leg.
 * Lower travel times are better; higher Jain fairness is fairer (1 = equal shares).
 */
public record RunMetrics(
        int tickBudget,
        int fleetSize,
        int arrived,
        int stranded,
        double fleetMean,
        double fleetP50,
        double fleetP90,
        double fleetP99,
        ClassMetrics emergency,
        ClassMetrics civilian,
        ClassMetrics vip,
        double jainCivilianFairness,
        double vehicleHoursTraveled,
        double throughputPerTick
) {
}
