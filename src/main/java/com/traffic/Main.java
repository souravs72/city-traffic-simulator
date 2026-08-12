package com.traffic;

import com.traffic.config.SimConfig;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.sim.Simulation;

import java.util.List;

/**
 * Entry point. Wires config → map → route → single-threaded sim.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SimConfig config = SimConfig.defaults();

        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        NodeId c = new NodeId(2);

        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A", 0, 0)
                .addNode(b, "B", 3, 0)
                .addNode(c, "C", 3, 4)
                .addEdge(new EdgeId(0), a, b, 3, 2)
                .addEdge(new EdgeId(1), b, c, 4, 2)
                .addEdge(new EdgeId(2), a, c, 10, 2)
                .build();

        Router router = Routers.create(config.routingAlgorithm(), graph);
        Path path = router.findPath(graph, a, c, EdgeCost.baseWeight())
                .orElseThrow(() -> new IllegalStateException("No path A→C"));

        Vehicle car = new Vehicle(
                new VehicleId(0),
                a,
                c,
                config.initialFuel(),
                path
        );

        Simulation sim = new Simulation(
                new TrafficState(graph),
                List.of(car),
                config.initialFuel()
        );
        int steps = sim.run(config.maxTicks());

        System.out.println("algorithm=" + config.routingAlgorithm()
                + " steps=" + steps
                + " arrived=" + car.arrived()
                + " fuelLeft=" + car.fuel()
                + " burned=" + car.fuelBurned());
    }
}
