package com.traffic.eval;

import com.traffic.model.signal.SignalNetwork;

import java.util.ArrayList;
import java.util.List;

/** Multi-seed sweeps with seed-dependent blueprints. */
public final class ScenarioSuite {

    public static final long[] DEFAULT_SEEDS = {42L, 43L, 44L, 45L, 46L};

    private ScenarioSuite() {
    }

    public static List<ExperimentResult> runDefault() {
        List<ExperimentResult> out = new ArrayList<>();
        for (MechanismProfile profile : MechanismProfile.coreCompare()) {
            out.addAll(ExperimentRunner.runSeeds(
                    ScenarioFixtures::emergencyCorridor,
                    profile,
                    100,
                    DEFAULT_SEEDS,
                    "emergency_corridor",
                    SignalNetwork.ControlMode.FLOW_GUARD));
        }
        long[] playSeeds = {42L, 43L, 44L};
        for (MechanismProfile profile : MechanismProfile.coreCompare()) {
            out.addAll(ExperimentRunner.runSeeds(
                    seed -> ScenarioFixtures.playgroundDemand(seed, 12),
                    profile,
                    120,
                    playSeeds,
                    "playground_demand",
                    SignalNetwork.ControlMode.FLOW_GUARD));
        }
        return out;
    }

    public static List<ExperimentResult> runAblation() {
        List<ExperimentResult> out = new ArrayList<>();
        long[] seeds = {42L, 43L, 44L};
        for (MechanismProfile profile : MechanismProfile.ablationSet()) {
            out.addAll(ExperimentRunner.runSeeds(
                    ScenarioFixtures::emergencyCorridor,
                    profile,
                    100,
                    seeds,
                    "emergency_corridor",
                    SignalNetwork.ControlMode.FLOW_GUARD));
            out.addAll(ExperimentRunner.runSeeds(
                    ScenarioFixtures::vipPlusEmergency,
                    profile,
                    120,
                    seeds,
                    "vip_plus_emergency",
                    SignalNetwork.ControlMode.FLOW_GUARD));
        }
        return out;
    }

    public static List<ExperimentResult> runSignalMatrix() {
        List<ExperimentResult> out = new ArrayList<>();
        SignalNetwork.ControlMode[] modes = {
                SignalNetwork.ControlMode.FLOW_GUARD,
                SignalNetwork.ControlMode.FIXED_CYCLE
        };
        for (SignalNetwork.ControlMode mode : modes) {
            for (MechanismProfile profile : MechanismProfile.coreCompare()) {
                out.addAll(ExperimentRunner.runSeeds(
                        ScenarioFixtures::emergencyCorridor,
                        profile,
                        100,
                        DEFAULT_SEEDS,
                        "emergency_corridor",
                        mode));
            }
        }
        long[] downtownSeeds = {42L, 43L};
        for (SignalNetwork.ControlMode mode : modes) {
            for (MechanismProfile profile : MechanismProfile.coreCompare()) {
                out.addAll(ExperimentRunner.runSeeds(
                        seed -> ScenarioFixtures.downtownDemand(seed, 16),
                        profile,
                        140,
                        downtownSeeds,
                        "downtown_demand",
                        mode));
            }
        }
        return out;
    }
}
