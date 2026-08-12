package com.traffic.eval;

import java.util.List;
import java.util.Objects;

/** One finished leg: manifest + metrics. Callers: ExperimentRunner, ResultWriter, tests. */
public record ExperimentResult(ExperimentManifest manifest, RunMetrics metrics) {
    public ExperimentResult {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(metrics, "metrics");
    }

    /** Multi-seed aggregate for one mechanism profile. */
    public static AggregateStats aggregate(String profile, List<RunMetrics> runs, List<RunMetrics> baseline) {
        return AggregateStats.from(profile, runs, baseline);
    }

    public record AggregateStats(
            String mechanismProfile,
            int n,
            double emergencyBestMean,
            double emergencyBestStdev,
            double emergencyP90Mean,
            double civilianMeanMean,
            double civilianP90Mean,
            double jainMean,
            double winRateVsBaselineEmergency
    ) {
        static AggregateStats from(String profile, List<RunMetrics> runs, List<RunMetrics> baseline) {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(runs, "runs");
            int n = runs.size();
            if (n == 0) {
                return new AggregateStats(profile, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            double[] emergBest = new double[n];
            double[] emergP90 = new double[n];
            double[] civMean = new double[n];
            double[] civP90 = new double[n];
            double[] jain = new double[n];
            for (int i = 0; i < n; i++) {
                RunMetrics m = runs.get(i);
                emergBest[i] = m.emergency().best() < 0 ? Double.NaN : m.emergency().best();
                emergP90[i] = m.emergency().p90();
                civMean[i] = m.civilian().mean();
                civP90[i] = m.civilian().p90();
                jain[i] = m.jainCivilianFairness();
            }
            double win = 0;
            if (baseline != null && baseline.size() == n) {
                int wins = 0;
                int compared = 0;
                for (int i = 0; i < n; i++) {
                    int a = runs.get(i).emergency().best();
                    int b = baseline.get(i).emergency().best();
                    if (a < 0 || b < 0) {
                        continue;
                    }
                    compared++;
                if (a <= b) {
                    wins++;
                }
                }
                win = compared == 0 ? 0 : (double) wins / compared;
            }
            return new AggregateStats(
                    profile,
                    n,
                    mean(emergBest),
                    stdev(emergBest),
                    mean(emergP90),
                    mean(civMean),
                    mean(civP90),
                    mean(jain),
                    win
            );
        }

        private static double mean(double[] xs) {
            double s = 0;
            int n = 0;
            for (double x : xs) {
                if (Double.isNaN(x)) {
                    continue;
                }
                s += x;
                n++;
            }
            return n == 0 ? 0 : s / n;
        }

        private static double stdev(double[] xs) {
            double m = mean(xs);
            double s = 0;
            int n = 0;
            for (double x : xs) {
                if (Double.isNaN(x)) {
                    continue;
                }
                double d = x - m;
                s += d * d;
                n++;
            }
            return n < 2 ? 0 : Math.sqrt(s / (n - 1));
        }
    }
}
