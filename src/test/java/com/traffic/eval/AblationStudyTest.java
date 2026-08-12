package com.traffic.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AblationStudyTest {

    @Test
    void emergencyBest_fullBeatsNone_onCorridorFixture() {
        var bp = ScenarioFixtures.emergencyCorridor(0L);
        int noneBest = ExperimentRunner.runLeg(bp, MechanismProfile.NONE, 120, 0L, "emergency_corridor")
                .metrics().emergency().best();
        int fullBest = ExperimentRunner.runLeg(bp, MechanismProfile.FULL, 120, 0L, "emergency_corridor")
                .metrics().emergency().best();
        assertTrue(noneBest > 0 && fullBest > 0);
        assertTrue(fullBest <= noneBest, "FULL=" + fullBest + " NONE=" + noneBest);
    }

    @Test
    void leaveOneOut_corridorOrSignal_stillCompetitiveWithNone() {
        var bp = ScenarioFixtures.emergencyCorridor(0L);
        int none = ExperimentRunner.runLeg(bp, MechanismProfile.NONE, 120, 0L, "emergency_corridor")
                .metrics().emergency().best();
        int minusCorridor = ExperimentRunner.runLeg(
                        bp, MechanismProfile.FULL_MINUS_CORRIDOR, 120, 0L, "emergency_corridor")
                .metrics().emergency().best();
        int minusSignal = ExperimentRunner.runLeg(
                        bp, MechanismProfile.FULL_MINUS_SIGNAL, 120, 0L, "emergency_corridor")
                .metrics().emergency().best();
        assertTrue(
                minusCorridor <= none || minusSignal <= none,
                "minusCorridor=" + minusCorridor + " minusSignal=" + minusSignal + " none=" + none
        );
    }

    @Test
    void vipPlusEmergency_fullRunsAndReportsMetrics() {
        var bp = ScenarioFixtures.vipPlusEmergency(42L);
        ExperimentResult full = ExperimentRunner.runLeg(bp, MechanismProfile.FULL, 140, 42L, "vip_plus_emergency");
        assertTrue(full.metrics().fleetSize() >= 6);
        assertTrue(full.metrics().jainCivilianFairness() > 0);
    }
}
