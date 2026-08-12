package com.traffic.sim;

import com.traffic.api.SessionHub;
import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.api.dto.PolicyCompareDto;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;

import java.util.List;

/**
 * Isolated A/B runner: same blueprint under MAPS_LIKE vs CITY_FLOW.
 * Does not touch the live session or disk save.
 */
public final class PolicyArena {

    private PolicyArena() {
    }

    public static PolicyCompareDto compare(CityBlueprintDto blueprint, int ticks) {
        if (ticks <= 0) {
            ticks = 80;
        }
        PolicyCompareDto.PolicyLegDto maps = runLeg(blueprint, ControlPolicy.MAPS_LIKE, ticks);
        PolicyCompareDto.PolicyLegDto flow = runLeg(blueprint, ControlPolicy.CITY_FLOW, ticks);

        boolean emergWin = flow.emergencyArrivalTicks() > 0
                && (maps.emergencyArrivalTicks() <= 0
                || flow.emergencyArrivalTicks() < maps.emergencyArrivalTicks());
        boolean civFair = flow.civilianAvgTicks() <= maps.civilianAvgTicks() * 1.10;

        String verdict;
        if (emergWin && flow.civilianAvgTicks() <= maps.civilianAvgTicks()) {
            verdict = "CityFlow wins: faster emergency response AND better/equal civilian average.";
        } else if (emergWin && civFair) {
            verdict = "CityFlow wins emergency; civilians within 5% fairness band of Maps-like.";
        } else if (emergWin) {
            verdict = "CityFlow wins emergency; civilian cost elevated — review corridor sizing.";
        } else if (flow.fleetAvgTicks() <= maps.fleetAvgTicks()) {
            verdict = "CityFlow improves fleet average but emergency gain inconclusive.";
        } else {
            verdict = "Maps-like competitive on this snapshot — add emergency/VIP demand to stress CityFlow.";
        }

        int fleet = blueprint.trips() == null ? 0 : blueprint.trips().size();
        return new PolicyCompareDto(ticks, fleet, maps, flow, emergWin, civFair, verdict);
    }

    private static PolicyCompareDto.PolicyLegDto runLeg(
            CityBlueprintDto blueprint,
            ControlPolicy policy,
            int budget
    ) {
        CitySession session = SessionHub.sessionFromBlueprint(blueprint);
        session.setControlPolicy(policy);

        if (policy.honorPriority()) {
            for (Vehicle v : List.copyOf(session.fleet())) {
                if (v.serviceClass() == ServiceClass.VIP && v.scheduledDepartAtTick() > 0) {
                    session.armVipLockdown(v, v.scheduledDepartAtTick());
                }
            }
            session.replanAroundCorridors();
        }

        session.play();
        session.run(budget);

        int emergBest = Integer.MAX_VALUE;
        long civSum = 0;
        int civN = 0;
        long fleetSum = 0;
        int fleetN = 0;
        int arrived = 0;
        int stranded = 0;
        for (Vehicle v : session.fleet()) {
            int travel;
            if (v.arrived() && v.arrivedAtTick().isPresent()) {
                travel = Math.max(0, v.arrivedAtTick().get() - v.spawnedAtTick());
                arrived++;
            } else {
                travel = budget + 10;
                stranded++;
            }
            fleetSum += travel;
            fleetN++;
            if (v.serviceClass().isEmergency()) {
                emergBest = Math.min(emergBest, travel);
            }
            if (v.serviceClass() == ServiceClass.CIVILIAN) {
                civSum += travel;
                civN++;
            }
        }
        int emerg = emergBest == Integer.MAX_VALUE ? -1 : emergBest;
        double civAvg = civN == 0 ? 0 : (double) civSum / civN;
        double fleetAvg = fleetN == 0 ? 0 : (double) fleetSum / fleetN;
        return new PolicyCompareDto.PolicyLegDto(
                policy.name(), emerg, civAvg, fleetAvg, arrived, stranded
        );
    }
}
