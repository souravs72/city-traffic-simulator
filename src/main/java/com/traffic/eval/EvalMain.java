package com.traffic.eval;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CLI: {@code mvn -q exec:java -Dexec.mainClass=com.traffic.eval.EvalMain -Dexec.args="--suite default"}
 */
public final class EvalMain {

    private EvalMain() {
    }

    public static void main(String[] args) throws Exception {
        String suite = "default";
        Path out = Path.of("results");
        for (int i = 0; i < args.length; i++) {
            if ("--suite".equals(args[i]) && i + 1 < args.length) {
                suite = args[++i];
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = Path.of(args[++i]);
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                System.out.println("Usage: EvalMain [--suite default|ablation|signals] [--out results]");
                return;
            }
        }

        List<ExperimentResult> results = switch (suite.toLowerCase(Locale.ROOT)) {
            case "ablation" -> ScenarioSuite.runAblation();
            case "signals" -> ScenarioSuite.runSignalMatrix();
            default -> ScenarioSuite.runDefault();
        };

        String batchId = suite + "-" + Instant.now().toString().replace(':', '-');
        Path dir = ResultWriter.write(out, batchId, results);
        System.out.printf(Locale.ROOT, "Wrote %d runs to %s%n", results.size(), dir.toAbsolutePath());

        Map<String, List<ExperimentResult>> byScenario = new LinkedHashMap<>();
        for (ExperimentResult r : results) {
            byScenario.computeIfAbsent(r.manifest().scenarioId(), k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<ExperimentResult>> e : byScenario.entrySet()) {
            printScenarioSummary(e.getKey(), e.getValue());
        }
    }

    private static void printScenarioSummary(String scenarioId, List<ExperimentResult> runs) {
        List<RunMetrics> none = new ArrayList<>();
        List<RunMetrics> full = new ArrayList<>();
        for (ExperimentResult r : runs) {
            if (!"FLOW_GUARD".equals(r.manifest().signalMode())) {
                continue;
            }
            if ("NONE".equals(r.manifest().mechanismProfile())) {
                none.add(r.metrics());
            } else if ("FULL".equals(r.manifest().mechanismProfile())) {
                full.add(r.metrics());
            }
        }
        if (none.isEmpty() || none.size() != full.size()) {
            return;
        }
        var agg = ExperimentResult.aggregate("FULL", full, none);
        System.out.printf(
                Locale.ROOT,
                "[%s] FULL vs NONE emerg not-worse=%.2f emergBestMean=%.2f±%.2f civMean=%.2f jain=%.3f (n=%d)%n",
                scenarioId,
                agg.winRateVsBaselineEmergency(),
                agg.emergencyBestMean(),
                agg.emergencyBestStdev(),
                agg.civilianMeanMean(),
                agg.jainMean(),
                agg.n()
        );
    }
}
