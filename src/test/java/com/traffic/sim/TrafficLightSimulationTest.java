package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;
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

        TrafficLight light = new TrafficLight("gate", Set.of(edge), 3, 2, LightColor.RED);
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
    void closedEdgeBlocksEntry() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        EdgeId edge = new EdgeId(0);
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addEdge(edge, a, b, 2, 1)
                .build();
        TrafficState traffic = new TrafficState(graph);
        traffic.close(edge);

        Vehicle car = new Vehicle(
                new VehicleId(0),
                a,
                b,
                10,
                new Path(List.of(edge), 2)
        );
        Simulation sim = new Simulation(traffic, List.of(car), 10);
        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.AtNode);
        traffic.reopen(edge);
        sim.step();
        assertTrue(car.position() instanceof VehiclePosition.OnEdge);
    }
}
