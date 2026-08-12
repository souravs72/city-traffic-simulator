package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;
import com.traffic.model.traffic.Accident;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.Path;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TrafficLightSimulationTest {

    @Test
    void carWaitsForGreenThenProceeds() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        EdgeId edge = new EdgeId(0);

        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addEdge(edge, a, b, 2, 1)
                .build();

        // RED for 2 ticks, then GREEN (yellow unused from red)
        TrafficLight light = new TrafficLight("gate", Set.of(edge), 3, 1, 2, LightColor.RED);
        SignalNetwork signals = new SignalNetwork(List.of(light));

        int fuel = 10;
        Vehicle car = new Vehicle(
                new VehicleId(0),
                a,
                b,
                fuel,
                new Path(List.of(edge), 2)
        );
        Simulation sim = new Simulation(
                new TrafficState(graph),
                signals,
                List.of(car),
                fuel
        );

        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.AtNode);

        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.AtNode);

        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.OnEdge);

        sim.run(10);
        assertTrue(car.arrived());
    }

    @Test
    void yellowBlocksNewEntry() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        EdgeId edge = new EdgeId(0);
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addEdge(edge, a, b, 2, 1)
                .build();

        Vehicle waiter = new Vehicle(
                new VehicleId(1), a, b, 10, new Path(List.of(edge), 2)
        );
        Simulation sim = new Simulation(
                new TrafficState(graph),
                new SignalNetwork(List.of(
                        new TrafficLight("y", Set.of(edge), 1, 5, 1, LightColor.YELLOW)
                )),
                List.of(waiter),
                10
        );
        sim.step();
        assertTrue(waiter.position() instanceof VehiclePosition.AtNode);
        assertFalse(sim.signals().isOpen(edge));
    }

    @Test
    void accidentShowsCrossThenClears() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        EdgeId edge = new EdgeId(0);
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addEdge(edge, a, b, 2, 1)
                .build();
        TrafficState traffic = new TrafficState(graph);
        Accident crash = traffic.reportAccident(edge, 2, "Duck crossing committee");
        assertTrue(crash.showCross());

        Vehicle car = new Vehicle(
                new VehicleId(0), a, b, 10, new Path(List.of(edge), 2)
        );
        Simulation sim = new Simulation(traffic, List.of(car), 10);

        sim.step(); // blocked by accident; accident ticks to 1
        assertTrue(car.position() instanceof VehiclePosition.AtNode);
        assertTrue(traffic.accidentOn(edge).isPresent());

        sim.step(); // accident ticks to 0 and clears at end of step
        // After this step accident may be cleared; car may enter if cleared before Phase B...
        // Order: Phase B (still closed if ticks was 1), Phase C tickAccidents clears.
        // So after step 2 with duration 2: start 2, after step1 remaining 1, after step2 remaining 0 removed.
        // Car still at node after step 2. Step 3 can enter.
        assertTrue(traffic.activeAccidents().isEmpty());
        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.OnEdge);
    }
}
