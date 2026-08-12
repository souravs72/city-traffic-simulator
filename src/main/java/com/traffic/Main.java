package com.traffic;

import com.traffic.config.SimConfig;
import com.traffic.model.traffic.Accident;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Routers;
import com.traffic.rules.AccidentFlavor;
import com.traffic.rules.DynamicEdgeCost;
import com.traffic.rules.Replanner;
import com.traffic.sim.DemoCity;
import com.traffic.sim.FleetFactory;
import com.traffic.sim.Simulation;

import java.util.List;

/**
 * Playable multi-car demo: lights, ✕ accidents, congestion costs, and automatic replan.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SimConfig config = SimConfig.defaults();
        DemoCity city = new DemoCity();
        TrafficState traffic = new TrafficState(city.graph);
        EdgeCost cost = new DynamicEdgeCost(traffic, config.congestionPenaltyPerCar());

        Accident crash = traffic.reportAccident(
                city.ac,
                config.accidentDurationTicks(),
                AccidentFlavor.randomCaption()
        );

        List<Vehicle> fleet = FleetFactory.spawn(
                city,
                config,
                cost,
                FleetFactory.defaultTrips(city)
        );

        Replanner replanner = new Replanner(
                Routers.create(config.routingAlgorithm(), city.graph),
                cost
        );

        Simulation sim = new Simulation(
                traffic,
                city.defaultSignals(config.lightTiming()),
                fleet,
                config.initialFuel(),
                replanner
        );

        System.out.println("=== City Traffic Simulator — fleet demo ===");
        System.out.println("cars=" + fleet.size()
                + " | lights=" + config.lightTiming()
                + " | router=" + config.routingAlgorithm());
        System.out.println("✕ " + crash.caption()
                + " on edge " + crash.edgeId().value()
                + " (" + crash.ticksRemaining() + " ticks)");
        System.out.println();

        int started = sim.tick();
        while (sim.tick() - started < config.maxTicks() && !sim.allArrived()) {
            sim.step();
            if (config.verboseTickLog() && sim.tick() % 2 == 0) {
                System.out.println("t=" + sim.tick()
                        + " arrived=" + sim.arrivedCount() + "/" + fleet.size()
                        + " replans=" + sim.totalReplans()
                        + " ✕=" + traffic.activeAccidents().size());
            }
        }

        System.out.println();
        System.out.println("done: steps=" + (sim.tick() - started)
                + " arrived=" + sim.arrivedCount() + "/" + fleet.size()
                + " totalReplans=" + sim.totalReplans());
        for (Vehicle car : fleet) {
            System.out.println("  car#" + car.id().value()
                    + " arrived=" + car.arrived()
                    + " replans=" + car.replanCount()
                    + " fuelLeft=" + car.fuel());
        }
    }
}
