package com.traffic.eval;

import com.traffic.model.priority.PriorityMechanisms;
import com.traffic.model.signal.SignalNetwork;

import java.util.Objects;

/**
 * Reproducible experiment description. Serialize with Jackson in {@link ResultWriter}.
 * Callers: ExperimentRunner, EvalMain, ResultWriter.
 */
public record ExperimentManifest(
        String runId,
        String scenarioId,
        long seed,
        int ticks,
        String policyLabel,
        String mechanismProfile,
        boolean priorityDeparture,
        boolean signalPreemption,
        boolean corridorBlocking,
        boolean softBufferRouting,
        String signalMode,
        boolean serialTick,
        String simConfigFingerprint
) {
    public ExperimentManifest {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(policyLabel, "policyLabel");
        Objects.requireNonNull(mechanismProfile, "mechanismProfile");
        Objects.requireNonNull(signalMode, "signalMode");
        Objects.requireNonNull(simConfigFingerprint, "simConfigFingerprint");
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be > 0");
        }
    }

    public static ExperimentManifest of(
            String runId,
            String scenarioId,
            long seed,
            int ticks,
            String policyLabel,
            PriorityMechanisms mechanisms,
            SignalNetwork.ControlMode signalMode,
            boolean serialTick,
            String simConfigFingerprint
    ) {
        PriorityMechanisms m = mechanisms == null ? PriorityMechanisms.none() : mechanisms;
        return new ExperimentManifest(
                runId,
                scenarioId,
                seed,
                ticks,
                policyLabel,
                m.profileName(),
                m.priorityDeparture(),
                m.signalPreemption(),
                m.corridorBlocking(),
                m.softBufferRouting(),
                signalMode == null ? SignalNetwork.ControlMode.FLOW_GUARD.name() : signalMode.name(),
                serialTick,
                simConfigFingerprint == null ? "default" : simConfigFingerprint
        );
    }
}
