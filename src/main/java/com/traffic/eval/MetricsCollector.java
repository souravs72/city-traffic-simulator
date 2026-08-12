package com.traffic.eval;

import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure metrics over a finished fleet. No HTTP / session dependencies.
 */
public final class MetricsCollector {

    private MetricsCollector() {
    }

    public static RunMetrics collect(List<Vehicle> fleet, int tickBudget) {
        Objects.requireNonNull(fleet, "fleet");
        if (tickBudget <= 0) {
            throw new IllegalArgumentException("tickBudget must be > 0");
        }
        List<Integer> all = new ArrayList<>();
        List<Integer> emerg = new ArrayList<>();
        List<Integer> civ = new ArrayList<>();
        List<Integer> vip = new ArrayList<>();
        int arrived = 0;
        int stranded = 0;
        long vht = 0;

        for (Vehicle v : fleet) {
            int travel = travelTicks(v, tickBudget);
            all.add(travel);
            vht += travel;
            if (v.arrived()) {
                arrived++;
            } else {
                stranded++;
            }
            ServiceClass sc = v.serviceClass();
            if (sc.isEmergency()) {
                emerg.add(travel);
            } else if (sc == ServiceClass.VIP) {
                vip.add(travel);
            } else if (sc == ServiceClass.CIVILIAN) {
                civ.add(travel);
            }
        }

        ClassMetrics emergency = summarize(emerg, tickBudget);
        ClassMetrics civilian = summarize(civ, tickBudget);
        ClassMetrics vipM = summarize(vip, tickBudget);
        Stats fleetStats = stats(all);
        double jain = jainIndex(civ);
        double throughput = (double) arrived / tickBudget;

        return new RunMetrics(
                tickBudget,
                fleet.size(),
                arrived,
                stranded,
                fleetStats.mean,
                fleetStats.p50,
                fleetStats.p90,
                fleetStats.p99,
                emergency,
                civilian,
                vipM,
                jain,
                vht,
                throughput
        );
    }

    static int travelTicks(Vehicle v, int tickBudget) {
        if (v.arrived() && v.arrivedAtTick().isPresent()) {
            return Math.max(0, v.arrivedAtTick().get() - v.spawnedAtTick());
        }
        return tickBudget + 10;
    }

    static ClassMetrics summarize(List<Integer> samples, int tickBudget) {
        if (samples.isEmpty()) {
            return ClassMetrics.empty();
        }
        int strandedThreshold = tickBudget + 10;
        int arrivedN = 0;
        int strandedN = 0;
        for (int t : samples) {
            if (t >= strandedThreshold) {
                strandedN++;
            } else {
                arrivedN++;
            }
        }
        Stats s = stats(samples);
        int best = samples.stream().mapToInt(Integer::intValue).min().orElse(-1);
        return new ClassMetrics(samples.size(), arrivedN, strandedN, s.mean, s.p50, s.p90, s.p99, best);
    }

    /**
     * Jain's fairness index on non-negative travel times. Empty → 1.0 (vacuously fair).
     */
    public static double jainIndex(List<Integer> samples) {
        if (samples == null || samples.isEmpty()) {
            return 1.0;
        }
        double sum = 0;
        double sumSq = 0;
        int n = 0;
        for (int x : samples) {
            if (x < 0) {
                continue;
            }
            sum += x;
            sumSq += (double) x * x;
            n++;
        }
        if (n == 0 || sumSq == 0) {
            return 1.0;
        }
        return (sum * sum) / (n * sumSq);
    }

    public static double percentile(List<Integer> sortedAsc, double p) {
        if (sortedAsc == null || sortedAsc.isEmpty()) {
            return 0;
        }
        if (p <= 0) {
            return sortedAsc.get(0);
        }
        if (p >= 100) {
            return sortedAsc.get(sortedAsc.size() - 1);
        }
        double rank = (p / 100.0) * (sortedAsc.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sortedAsc.get(lo);
        }
        double w = rank - lo;
        return sortedAsc.get(lo) * (1 - w) + sortedAsc.get(hi) * w;
    }

    private static Stats stats(List<Integer> samples) {
        if (samples.isEmpty()) {
            return new Stats(0, 0, 0, 0);
        }
        List<Integer> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        double sum = 0;
        for (int v : sorted) {
            sum += v;
        }
        return new Stats(
                sum / sorted.size(),
                percentile(sorted, 50),
                percentile(sorted, 90),
                percentile(sorted, 99)
        );
    }

    private record Stats(double mean, double p50, double p90, double p99) {
    }
}
