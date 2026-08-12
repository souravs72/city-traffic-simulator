package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.routing.EdgeCost;
import com.traffic.rules.DynamicEdgeCost;
import com.traffic.sim.parallel.SimExecutor;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ParallelTickEquivalenceTest {

    @Test
    void serialAndParallelMatchArrivalsAndOccupancy() {
        SimConfig config = SimConfig.defaults().withParallelRoutingThreshold(4);
        CityGenConfig gen = CityGenConfig.playground();
        var city = GridCityGenerator.generate(gen);
        var graph = city.snapshot();
        EdgeCost cost = new DynamicEdgeCost(new TrafficState(graph), 2);
        List<FleetFactory.Trip> trips = FleetFactory.randomTrips(graph, 6, 99L);
        List<Vehicle> fleetA = FleetFactory.spawn(graph, config, cost, trips);
        List<Vehicle> fleetB = FleetFactory.spawn(graph, config, cost, trips);

        TrafficState trafficA = new TrafficState(graph);
        TrafficState trafficB = new TrafficState(graph);
        SignalNetwork signalsA = SignalNetwork.none();
        SignalNetwork signalsB = SignalNetwork.none();
        try (SimExecutor exec = SimExecutor.createDefault()) {
            Simulation serial = new Simulation(
                    trafficA, signalsA, fleetA, config.initialFuel(), null, true, 4,
                    new CorridorBoard(), ControlPolicy.MAPS_LIKE, false, exec);
            Simulation parallel = new Simulation(
                    trafficB, signalsB, fleetB, config.initialFuel(), null, true, 4,
                    new CorridorBoard(), ControlPolicy.MAPS_LIKE, true, exec);

            serial.run(40);
            parallel.run(40);

            assertEquals(serial.arrivedCount(), parallel.arrivedCount());
            assertEquals(
                    fleetA.stream().map(v -> v.arrived() ? v.id().value() : -1).collect(Collectors.toList()),
                    fleetB.stream().map(v -> v.arrived() ? v.id().value() : -1).collect(Collectors.toList())
            );
            for (var edge : graph.edges()) {
                assertEquals(trafficA.occupancy(edge.id()), trafficB.occupancy(edge.id()),
                        "occupancy " + edge.id());
            }
        }
    }
}
