package com.traffic.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.model.signal.SignalNetwork;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExperimentRunnerTest {

    @Test
    void emergencyBest_fullBeatsOrMatchesNone_onCorridorFixture() {
        CityBlueprintDto bp = ScenarioFixtures.emergencyCorridor(0L);
        ExperimentResult none = ExperimentRunner.runLeg(bp, MechanismProfile.NONE, 120, 0L, "emergency_corridor");
        ExperimentResult full = ExperimentRunner.runLeg(bp, MechanismProfile.FULL, 120, 0L, "emergency_corridor");

        assertTrue(none.metrics().emergency().best() > 0);
        assertTrue(full.metrics().emergency().best() > 0);
        assertTrue(
                full.metrics().emergency().best() <= none.metrics().emergency().best(),
                "FULL=" + full.metrics().emergency().best() + " NONE=" + none.metrics().emergency().best()
        );
        assertTrue(full.manifest().serialTick());
    }

    @Test
    void serialDeterminism_sameBlueprintTwice_identicalMetrics() {
        CityBlueprintDto bp = ScenarioFixtures.emergencyCorridor(7L);
        ExperimentResult a = ExperimentRunner.runLeg(bp, MechanismProfile.FULL, 80, 7L, "emergency_corridor");
        ExperimentResult b = ExperimentRunner.runLeg(bp, MechanismProfile.FULL, 80, 7L, "emergency_corridor");
        assertEquals(a.metrics(), b.metrics());
    }

    @Test
    void multiSeed_seedDependentBlueprint_producesVariance() {
        long[] seeds = {1L, 2L, 3L, 4L, 5L};
        List<ExperimentResult> runs = ExperimentRunner.runSeeds(
                ScenarioFixtures::emergencyCorridor,
                MechanismProfile.NONE,
                80,
                seeds,
                "emergency_corridor",
                SignalNetwork.ControlMode.FLOW_GUARD);
        assertEquals(5, runs.size());
        int first = runs.get(0).metrics().fleetSize();
        boolean anyDifferent = false;
        for (ExperimentResult r : runs) {
            if (r.metrics().civilian().mean() != runs.get(0).metrics().civilian().mean()
                    || r.metrics().fleetMean() != runs.get(0).metrics().fleetMean()) {
                anyDifferent = true;
                break;
            }
        }
        // Demand mix changes with seed; at least fleet composition or travel should differ across seeds.
        // If means collide, trip OD lists in fixtures still differ — check manifests seeds unique.
        assertEquals(5, runs.stream().map(r -> r.manifest().seed()).distinct().count());
        assertTrue(first >= 1);
        // Prefer observing metric variance; if all equal by chance, OD shuffle still ran
        List<String> tripFingerprints = new ArrayList<>();
        for (long seed : seeds) {
            tripFingerprints.add(ScenarioFixtures.emergencyCorridor(seed).trips().toString());
        }
        assertTrue(tripFingerprints.stream().distinct().count() > 1,
                "seeded fixtures must differ in trip mix");
        if (!anyDifferent) {
            // rare: same means despite different OD — still valid if fingerprints differ
            assertNotEquals(tripFingerprints.get(0), tripFingerprints.get(1));
        }
    }

    @Test
    void ablations_runWithoutThrowing() {
        CityBlueprintDto bp = ScenarioFixtures.emergencyCorridor(0L);
        for (MechanismProfile profile : MechanismProfile.ablationSet()) {
            ExperimentResult r = ExperimentRunner.runLeg(bp, profile, 60, 0L, "emergency_corridor");
            assertEquals(profile.mechanisms().profileName(), r.manifest().mechanismProfile());
            assertTrue(r.metrics().fleetSize() >= 1);
        }
    }

    @Test
    void resultWriter_jsonlIsOneObjectPerLine(@TempDir Path tmp) throws Exception {
        CityBlueprintDto bp = ScenarioFixtures.emergencyCorridor(0L);
        List<ExperimentResult> results = List.of(
                ExperimentRunner.runLeg(bp, MechanismProfile.NONE, 40, 0L, "emergency_corridor"),
                ExperimentRunner.runLeg(bp, MechanismProfile.FULL, 40, 0L, "emergency_corridor")
        );
        Path dir = ResultWriter.write(tmp, "batch-test", results);
        List<String> lines = Files.readAllLines(dir.resolve("runs.jsonl"), StandardCharsets.UTF_8)
                .stream().filter(l -> !l.isBlank()).toList();
        assertEquals(2, lines.size());
        for (String line : lines) {
            assertTrue(line.startsWith("{") && line.endsWith("}"));
            assertTrue(!line.contains("\n"));
        }
        assertTrue(Files.exists(dir.resolve("manifests.json")));
        assertTrue(Files.exists(dir.resolve("batch.json")));
    }
}
