package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.Path;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PrioritySignalPassTest {

    @Test
    void ambulanceDoesNotWaitOnRedWhenOpposingIsOnlyCivilian() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        NodeId c = new NodeId(2);
        EdgeId ns = new EdgeId(0);
        EdgeId ew = new EdgeId(1);
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A").addNode(b, "B").addNode(c, "C")
                .addEdge(ns, a, b, 1, 2)
                .addEdge(ew, a, c, 1, 2)
                .build();

        TrafficLight nsLight = new TrafficLight("NS", Set.of(ns), new LightTiming(4, 1, 4), LightColor.RED);
        TrafficLight ewLight = new TrafficLight("EW", Set.of(ew), new LightTiming(4, 1, 4), LightColor.GREEN);
        SignalNetwork signals = new SignalNetwork(
                List.of(nsLight, ewLight), List.of(new SignalNetwork.Pair(nsLight, ewLight)));

        Vehicle ambulance = new Vehicle(
                new VehicleId(0), a, b, 40, new Path(List.of(ns), 1), 1, 1, 0, "Amb", ServiceClass.AMBULANCE, 0);
        Vehicle civilian = new Vehicle(
                new VehicleId(1), a, c, 40, new Path(List.of(ew), 1), 1, 1, 0, "Civ", ServiceClass.CIVILIAN, 0);

        Simulation sim = new Simulation(
                new TrafficState(graph), signals, List.of(ambulance, civilian), 80,
                null, false, 8, new CorridorBoard(), ControlPolicy.CITY_FLOW);
        sim.step();

        assertEquals(LightColor.GREEN, nsLight.color(), "ambulance approach must cut green");
        assertTrue(ambulance.position() instanceof VehiclePosition.OnEdge,
                "ambulance must not sit on red for civilians");
    }

    @Test
    void vipYieldsOnlyToHigherEmergencyOnConflict() {
        NodeId a = new NodeId(0);
        NodeId b = new NodeId(1);
        NodeId c = new NodeId(2);
        EdgeId ns = new EdgeId(0);
        EdgeId ew = new EdgeId(1);
        RoadGraph graph = new GraphBuilder()
                .addNode(a, "A").addNode(b, "B").addNode(c, "C")
                .addEdge(ns, a, b, 1, 1)
                .addEdge(ew, a, c, 1, 1)
                .build();

        TrafficLight nsLight = new TrafficLight("NS", Set.of(ns), new LightTiming(4, 1, 4), LightColor.GREEN);
        TrafficLight ewLight = new TrafficLight("EW", Set.of(ew), new LightTiming(4, 1, 4), LightColor.RED);
        SignalNetwork signals = new SignalNetwork(
                List.of(nsLight, ewLight), List.of(new SignalNetwork.Pair(nsLight, ewLight)));

        Vehicle vip = new Vehicle(
                new VehicleId(0), a, b, 40, new Path(List.of(ns), 1), 1, 1, 0, "VIP", ServiceClass.VIP, 0);
        Vehicle fire = new Vehicle(
                new VehicleId(1), a, c, 40, new Path(List.of(ew), 1), 1, 1, 0, "Fire", ServiceClass.FIRE, 0);

        Simulation sim = new Simulation(
                new TrafficState(graph), signals, List.of(vip, fire), 80,
                null, false, 8, new CorridorBoard(), ControlPolicy.CITY_FLOW);
        sim.step();

        assertEquals(LightColor.GREEN, ewLight.color());
        assertTrue(fire.position() instanceof VehiclePosition.OnEdge);
        // VIP must wait — stopping benefits the system (higher-rank fire).
        assertTrue(vip.position() instanceof VehiclePosition.AtNode);
    }
}
