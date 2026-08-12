package com.traffic.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Writes batch.json, manifests.json, runs.jsonl (one object per line), summary.csv. */
public final class ResultWriter {

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private ResultWriter() {
    }

    public static Path write(Path resultsRoot, String batchId, List<ExperimentResult> results) throws IOException {
        Objects.requireNonNull(resultsRoot, "resultsRoot");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(results, "results");
        Path dir = resultsRoot.resolve(batchId);
        Files.createDirectories(dir);

        List<ExperimentManifest> manifests = new ArrayList<>(results.size());
        for (ExperimentResult r : results) {
            manifests.add(r.manifest());
        }
        PRETTY.writeValue(dir.resolve("manifests.json").toFile(), manifests);

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("batchId", batchId);
        batch.put("runCount", results.size());
        batch.put("scenarioIds", results.stream().map(r -> r.manifest().scenarioId()).distinct().toList());
        batch.put("profiles", results.stream().map(r -> r.manifest().mechanismProfile()).distinct().toList());
        PRETTY.writeValue(dir.resolve("batch.json").toFile(), batch);

        StringBuilder lines = new StringBuilder();
        for (ExperimentResult r : results) {
            lines.append(COMPACT.writeValueAsString(r)).append('\n');
        }
        Files.writeString(dir.resolve("runs.jsonl"), lines.toString(), StandardCharsets.UTF_8);

        StringBuilder csvBody = new StringBuilder();
        csvBody.append("scenario,seed,profile,signalMode,emergBest,emergP90,civMean,civP90,jain,arrived,stranded\n");
        for (ExperimentResult r : results) {
            ExperimentManifest m = r.manifest();
            RunMetrics met = r.metrics();
            csvBody.append(csvEscape(m.scenarioId())).append(',')
                    .append(m.seed()).append(',')
                    .append(csvEscape(m.mechanismProfile())).append(',')
                    .append(csvEscape(m.signalMode())).append(',')
                    .append(met.emergency().best()).append(',')
                    .append(fmt(met.emergency().p90())).append(',')
                    .append(fmt(met.civilian().mean())).append(',')
                    .append(fmt(met.civilian().p90())).append(',')
                    .append(fmt(met.jainCivilianFairness())).append(',')
                    .append(met.arrived()).append(',')
                    .append(met.stranded()).append('\n');
        }
        Files.writeString(dir.resolve("summary.csv"), csvBody.toString(), StandardCharsets.UTF_8);
        return dir;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
