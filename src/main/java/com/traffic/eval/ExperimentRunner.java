package com.traffic.eval;

import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.blueprint.CityBlueprints;
import com.traffic.config.SimConfig;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.PriorityMechanisms;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.sim.CitySession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * Isolated experiment legs (no live API session / disk save). Serial ticks by default.
 * {@code seed} must change the blueprint via {@link #runSeeds(LongFunction, ...)} or a
 * seed-dependent fixture; it is recorded in the manifest for reproduction.
 */
public final class ExperimentRunner {

    private ExperimentRunner() {
    }

    public static ExperimentResult runLeg(
            CityBlueprintDto blueprint,
            PriorityMechanisms mechanisms,
            int ticks,
            long seed,
            String scenarioId,
            SignalNetwork.ControlMode signalMode,
            boolean serialTick
    ) {
        Objects.requireNonNull(blueprint, "blueprint");
        PriorityMechanisms mech = Objects.requireNonNullElse(mechanisms, PriorityMechanisms.none());
        int budget = ticks <= 0 ? 80 : ticks;
        SignalNetwork.ControlMode mode =
                signalMode == null ? SignalNetwork.ControlMode.FLOW_GUARD : signalMode;

        CitySession session = CityBlueprints.restore(blueprint);
        session.setForceSerialTick(serialTick);
        ControlPolicy label = mech.any() ? ControlPolicy.CITY_FLOW : ControlPolicy.MAPS_LIKE;
        session.setControlPolicy(label);
        session.setMechanisms(mech);
        session.setSignalControlMode(mode);

        // VIP corridors are armed inside play() when corridorBlocking is on — do not double-arm.
        session.play();
        session.run(budget);

        RunMetrics metrics = MetricsCollector.collect(session.fleet(), budget);
        ExperimentManifest manifest = ExperimentManifest.of(
                UUID.randomUUID().toString().substring(0, 8),
                scenarioId == null ? "adhoc" : scenarioId,
                seed,
                budget,
                label.name(),
                mech,
                mode,
                serialTick,
                fingerprint(session.config())
        );
        return new ExperimentResult(manifest, metrics);
    }

    public static ExperimentResult runLeg(
            CityBlueprintDto blueprint,
            MechanismProfile profile,
            int ticks,
            long seed,
            String scenarioId
    ) {
        return runLeg(
                blueprint,
                profile.mechanisms(),
                ticks,
                seed,
                scenarioId,
                SignalNetwork.ControlMode.FLOW_GUARD,
                true
        );
    }

    /**
     * Multi-seed sweep. {@code blueprintForSeed} must produce a seed-dependent blueprint
     * (topology and/or demand); otherwise replicates are identical and variance is meaningless.
     */
    public static List<ExperimentResult> runSeeds(
            LongFunction<CityBlueprintDto> blueprintForSeed,
            MechanismProfile profile,
            int ticks,
            long[] seeds,
            String scenarioId,
            SignalNetwork.ControlMode signalMode
    ) {
        Objects.requireNonNull(blueprintForSeed, "blueprintForSeed");
        Objects.requireNonNull(seeds, "seeds");
        List<ExperimentResult> out = new ArrayList<>(seeds.length);
        for (long seed : seeds) {
            CityBlueprintDto bp = Objects.requireNonNull(
                    blueprintForSeed.apply(seed), "blueprintForSeed.apply");
            out.add(runLeg(
                    bp,
                    profile.mechanisms(),
                    ticks,
                    seed,
                    scenarioId,
                    signalMode,
                    true
            ));
        }
        return out;
    }

    private static String fingerprint(SimConfig config) {
        return "algo=" + config.routingAlgorithm()
                + ";fuel=" + config.initialFuel()
                + ";cong=" + config.congestionPenaltyPerCar()
                + ";prt=" + config.parallelRoutingThreshold();
    }
}
