package com.traffic;

import com.traffic.config.SimConfig;
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
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.rules.DynamicEdgeCost;
import com.traffic.sim.Simulation;

import java.util.List;
import java.util.Set;

/**
 * Entry point. Wires config → map → signals → route → single-threaded sim.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SimConfig config = SimConfig.defaults();

        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        NodeId c = new NodeId(2);
        EdgeId ab = new EdgeId(0);
        EdgeId bc = new EdgeId(1);
        EdgeId ac = new EdgeId(2);

        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A", 0, 0)
                .addNode(b, "B", 3, 0)
                .addNode(c, "C", 3, 4)
                .addEdge(ab, a, b, 3, 2)
                .addEdge(bc, b, c, 4, 2)
                .addEdge(ac, a, c, 10, 2)
                .build();

        TrafficState traffic = new TrafficState(graph);
        SignalNetwork signals = new SignalNetwork(List.of(
                new TrafficLight("A-out", Set.of(ab, ac), 4, 2, LightColor.GREEN)
        ));

        EdgeCost cost = new DynamicEdgeCost(traffic, 2);
        Router router = Routers.create(config.routingAlgorithm(), graph);
        Path path = router.findPath(graph, a, c, cost)
                .orElseThrow(() -> new IllegalStateException("No path A→C"));

        Vehicle car = new Vehicle(
                new VehicleId(0),
                a,
                c,
                config.initialFuel(),
                path
        );

        Simulation sim = new Simulation(
                traffic,
                signals,
                List.of(car),
                config.initialFuel()
        );
        int steps = sim.run(config.maxTicks());

        System.out.println("algorithm=" + config.routingAlgorithm()
                + " pathCost=" + path.totalCost()
                + " hops=" + path.hopCount()
                + " steps=" + steps
                + " arrived=" + car.arrived()
                + " fuelLeft=" + car.fuel()
                + " burned=" + car.fuelBurned());
    }
}
