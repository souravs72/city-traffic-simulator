package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.config.SimConfig;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.routing.Path;
import com.traffic.routing.Routers;
import com.traffic.rules.DynamicEdgeCost;
import com.traffic.rules.Replanner;

import java.util.List;

import org.junit.jupiter.api.Test;

class FleetSimulationTest {

    @Test
    void fleetReachesHarborAroundAccident() {
        DemoCity city = new DemoCity();
        SimConfig config = SimConfig.defaults();
        TrafficState traffic = new TrafficState(city.graph);
        traffic.reportAccident(city.ac, 50, "Banana peel pileup");

        var cost = new DynamicEdgeCost(traffic, config.congestionPenaltyPerCar());
        List<Vehicle> fleet = FleetFactory.spawn(
                city,
                config,
                cost,
                List.of(
                        new FleetFactory.Trip(city.a, city.c, "Hopper"),
                        new FleetFactory.Trip(city.a, city.c, "Seeker")
                )
        );

        Replanner replanner = Replanner.withFixedCost(
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

        sim.run(80);
        assertTrue(sim.allArrived(), "fleet should arrive despite ✕ on shortcut");
        for (Vehicle car : fleet) {
            assertTrue(car.arrived());
            assertTrue(car.fuelLedger() == config.initialFuel());
        }
    }

    @Test
    void replansWhenPlannedEdgeIsClosed() {
        DemoCity city = new DemoCity();
        SimConfig config = SimConfig.defaults();
        TrafficState traffic = new TrafficState(city.graph);
        traffic.reportAccident(city.ac, 40, "Mime traffic jam");

        Vehicle car = new Vehicle(
                new VehicleId(0),
                city.a,
                city.c,
                config.initialFuel(),
                new Path(List.of(city.ac), 8)
        );

        var router = Routers.create(config.routingAlgorithm(), city.graph);
        var dynamic = new DynamicEdgeCost(traffic, 5);
        Simulation sim = new Simulation(
                traffic,
                city.defaultSignals(config.lightTiming()),
                List.of(car),
                config.initialFuel(),
                Replanner.withFixedCost(router, dynamic)
        );

        sim.run(60);
        assertTrue(car.arrived());
        assertTrue(car.replanCount() >= 1);
        assertTrue(sim.totalReplans() >= 1);
    }
}
