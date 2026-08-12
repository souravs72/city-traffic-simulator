package com.traffic.eval;

import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.api.dto.PolicyCompareDto;
import com.traffic.model.priority.ControlPolicy;

/**
 * Isolated A/B compare for API / UI. Callers: SessionHub (via shim or direct).
 * Lives in eval so sim does not depend upward. User: fix review findings.
 */
public final class PolicyArena {

    private PolicyArena() {
    }

    public static PolicyCompareDto compare(CityBlueprintDto blueprint, int ticks) {
        if (ticks <= 0) {
            ticks = 80;
        }
        String scenario = blueprint.preset() == null ? "compare" : blueprint.preset();
        ExperimentResult maps = ExperimentRunner.runLeg(
                blueprint, MechanismProfile.NONE, ticks, 0L, scenario);
        ExperimentResult flow = ExperimentRunner.runLeg(
                blueprint, MechanismProfile.FULL, ticks, 0L, scenario);

        PolicyCompareDto.PolicyLegDto mapsLeg = toLeg(ControlPolicy.MAPS_LIKE, maps.metrics());
        PolicyCompareDto.PolicyLegDto flowLeg = toLeg(ControlPolicy.CITY_FLOW, flow.metrics());

        boolean emergWin = flowLeg.emergencyArrivalTicks() > 0
                && (mapsLeg.emergencyArrivalTicks() <= 0
                || flowLeg.emergencyArrivalTicks() < mapsLeg.emergencyArrivalTicks());
        boolean civFair = flowLeg.civilianAvgTicks() <= mapsLeg.civilianAvgTicks() * 1.10;

        String verdict;
        if (emergWin && flowLeg.civilianAvgTicks() <= mapsLeg.civilianAvgTicks()) {
            verdict = "CityFlow wins: faster emergency response AND better/equal civilian average.";
        } else if (emergWin && civFair) {
            verdict = "CityFlow wins emergency; civilians within fairness band of Maps-like.";
        } else if (emergWin) {
            verdict = "CityFlow wins emergency; civilian cost elevated — review corridor sizing.";
        } else if (flowLeg.fleetAvgTicks() <= mapsLeg.fleetAvgTicks()) {
            verdict = "CityFlow improves fleet average but emergency gain inconclusive.";
        } else {
            verdict = "Maps-like competitive on this snapshot — add emergency/VIP demand to stress CityFlow.";
        }

        int fleet = blueprint.trips() == null ? 0 : blueprint.trips().size();
        return new PolicyCompareDto(
                ticks,
                fleet,
                mapsLeg,
                flowLeg,
                emergWin,
                civFair,
                verdict,
                maps.metrics().fleetP90(),
                flow.metrics().fleetP90(),
                maps.metrics().jainCivilianFairness(),
                flow.metrics().jainCivilianFairness(),
                flow.metrics().emergency().p90(),
                maps.metrics().emergency().p90()
        );
    }

    private static PolicyCompareDto.PolicyLegDto toLeg(ControlPolicy policy, RunMetrics m) {
        return new PolicyCompareDto.PolicyLegDto(
                policy.name(),
                m.emergency().best(),
                m.civilian().mean(),
                m.fleetMean(),
                m.arrived(),
                m.stranded(),
                m.fleetP90(),
                m.civilian().p90(),
                m.emergency().p90(),
                m.jainCivilianFairness()
        );
    }
}
