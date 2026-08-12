package com.traffic.eval;

/**
 * Per-cohort travel-time statistics (ticks). Stranded vehicles use {@code tickBudget + 10}.
 */
public record ClassMetrics(
        int count,
        int arrived,
        int stranded,
        double mean,
        double p50,
        double p90,
        double p99,
        int best
) {
    public static ClassMetrics empty() {
        return new ClassMetrics(0, 0, 0, 0, 0, 0, 0, -1);
    }
}
