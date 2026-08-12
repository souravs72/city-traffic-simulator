package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void soloLightNeverBlocksWhenThereIsNoOpposingPhase() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        EdgeId edge = new EdgeId(0);

        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addEdge(edge, a, b, 2, 1)
                .build();

        // Starts red, but pressure control opens solo approaches immediately.
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
        assertEquals(LightColor.GREEN, light.color());
        assertTrue(car.position() instanceof VehiclePosition.OnEdge);

        sim.run(10);
        assertTrue(car.arrived());
    }

    @Test
    void yellowStillBlocksDuringPairClearance() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        NodeId c = new NodeId(2);
        EdgeId ns = new EdgeId(0);
        EdgeId ew = new EdgeId(1);
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addNode(c, "C")
                .addEdge(ns, a, b, 2, 1)
                .addEdge(ew, a, c, 2, 1)
                .build();

        TrafficLight nsLight = new TrafficLight("NS", Set.of(ns), 2, 3, 2, LightColor.YELLOW);
        TrafficLight ewLight = new TrafficLight("EW", Set.of(ew), 2, 3, 2, LightColor.RED);
        SignalNetwork signals = new SignalNetwork(
                List.of(nsLight, ewLight),
                List.of(new SignalNetwork.Pair(nsLight, ewLight))
        );

        Vehicle waiter = new Vehicle(
                new VehicleId(1), a, b, 10, new Path(List.of(ns), 2)
        );
        Simulation sim = new Simulation(
                new TrafficState(graph),
                signals,
                List.of(waiter),
                10
        );
        sim.step();
        // Still clearing yellow — no new entry on NS this tick.
        assertTrue(waiter.position() instanceof VehiclePosition.AtNode);
        assertFalse(sim.signals().isOpen(ns));
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

        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.AtNode);
        assertTrue(traffic.accidentOn(edge).isPresent());

        sim.step();
        assertTrue(traffic.activeAccidents().isEmpty());
        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.OnEdge);
    }
}
