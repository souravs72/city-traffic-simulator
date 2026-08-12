package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.routing.RoutingAlgorithm;

import java.util.List;

import org.junit.jupiter.api.Test;

class SimulationTest {

    private final NodeId a = new NodeId(0);
    private final NodeId b = new NodeId(1);
    private final NodeId c = new NodeId(2);

    @Test
    void singleCarReachesDestination() {
        RoadGraph graph = tinyCity();
        Router router = Routers.create(RoutingAlgorithm.DIJKSTRA, graph);
        Path path = router.findPath(graph, a, c, EdgeCost.baseWeight()).orElseThrow();

        int fuel = 20;
        Vehicle car = new Vehicle(new VehicleId(0), a, c, fuel, path);
        TrafficState traffic = new TrafficState(graph);
        Simulation sim = new Simulation(traffic, List.of(car), fuel);

        int steps = sim.run(50);
        assertTrue(car.arrived());
        assertTrue(steps > 0);
        assertEquals(fuel, car.fuelLedger());
        assertTrue(car.position() instanceof VehiclePosition.AtNode at && at.node().equals(c));
    }

    @Test
    void secondCarWaitsWhenCapacityFull() {
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A")
                .addNode(b, "B")
                .addEdge(new EdgeId(0), a, b, 3, 1)
                .build();
        Path path = new Path(List.of(new EdgeId(0)), 3);
        int fuel = 10;
        Vehicle v0 = new Vehicle(new VehicleId(0), a, b, fuel, path);
        Vehicle v1 = new Vehicle(new VehicleId(1), a, b, fuel, path);
        TrafficState traffic = new TrafficState(graph);
        Simulation sim = new Simulation(traffic, List.of(v0, v1), fuel);

        sim.step();
        assertTrue(v0.position() instanceof VehiclePosition.OnEdge);
        assertTrue(v1.position() instanceof VehiclePosition.AtNode);
        assertEquals(1, traffic.occupancy(new EdgeId(0)));

        sim.run(20);
        assertTrue(v0.arrived());
        assertTrue(v1.arrived());
        assertEquals(0, traffic.occupancy(new EdgeId(0)));
    }

    private RoadGraph tinyCity() {
        return new GraphBuilder()
                .addNode(a, "A", 0, 0)
                .addNode(b, "B", 3, 0)
                .addNode(c, "C", 3, 4)
                .addEdge(new EdgeId(0), a, b, 3, 2)
                .addEdge(new EdgeId(1), b, c, 4, 2)
                .addEdge(new EdgeId(2), a, c, 10, 2)
                .build();
    }
}
