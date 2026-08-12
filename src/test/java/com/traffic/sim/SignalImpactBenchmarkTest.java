package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Rigorous proof that signals matter — and that FlowGuard beats fixed cycles
 * for decongestion without needlessly blocking free approaches.
 */
class SignalImpactBenchmarkTest {

    private record Metrics(int makespan, long totalTravel, int stillQueued) {
    }

    @Test
    void oneSidedDemand_flowGuardNearOpen_fixedCycleWastesGreen() {
        // Only NS traffic: fixed cycle still burns half its time on empty EW.
        Metrics open = runOneSided(policyNone(), 60);
        Metrics fixed = runOneSided(policyFixed(3, 1, 3), 60);
        Metrics flow = runOneSided(policyFlowGuard(3, 1, 3), 60);

        System.out.printf(
                "One-sided — open(makespan=%d total=%d) fixed(%d,%d) flowGuard(%d,%d)%n",
                open.makespan(), open.totalTravel(),
                fixed.makespan(), fixed.totalTravel(),
                flow.makespan(), flow.totalTravel()
        );

        assertEquals(0, open.stillQueued());
        assertEquals(0, flow.stillQueued());
        assertTrue(fixed.totalTravel() > open.totalTravel(),
                "fixed-cycle must cost more than always-open; fixed="
                        + fixed.totalTravel() + " open=" + open.totalTravel());
        assertTrue(flow.totalTravel() < fixed.totalTravel(),
                "FlowGuard should beat fixed on one-sided demand; flow="
                        + flow.totalTravel() + " fixed=" + fixed.totalTravel());
        assertTrue(flow.makespan() <= open.makespan() + 2,
                "FlowGuard should stay near always-open; flow="
                        + flow.makespan() + " open=" + open.makespan());
    }

    @Test
    void competingCross_flowGuardBeatsFixedAndClears() {
        Metrics open = runCompeting(policyNone(), 120);
        Metrics fixed = runCompeting(policyFixed(2, 1, 4), 120);
        Metrics flow = runCompeting(policyFlowGuard(4, 1, 4), 120);

        System.out.printf(
                "Competing — open(makespan=%d total=%d q=%d) fixed(%d,%d q=%d) flowGuard(%d,%d q=%d)%n",
                open.makespan(), open.totalTravel(), open.stillQueued(),
                fixed.makespan(), fixed.totalTravel(), fixed.stillQueued(),
                flow.makespan(), flow.totalTravel(), flow.stillQueued()
        );

        assertTrue(fixed.totalTravel() > open.totalTravel(),
                "fixed-cycle should cost more than always-open");
        assertTrue(flow.totalTravel() < fixed.totalTravel(),
                "FlowGuard should beat fixed cycle; flow="
                        + flow.totalTravel() + " fixed=" + fixed.totalTravel());
        assertTrue(flow.makespan() <= fixed.makespan(),
                "FlowGuard makespan should be <= fixed");
        assertEquals(0, flow.stillQueued(), "FlowGuard left cars stranded");
    }

    @Test
    void flowGuardDoesNotBlockWhenOpposingApproachIsEmpty() {
        NodeId n = new NodeId(0);
        NodeId e = new NodeId(1);
        NodeId s = new NodeId(2);
        EdgeId north = new EdgeId(0);
        EdgeId east = new EdgeId(1);
        RoadGraph graph = new GraphBuilder()
                .addNode(n, "N").addNode(e, "E").addNode(s, "S")
                .addEdge(north, n, s, 1, 2)
                .addEdge(east, e, s, 1, 2)
                .build();

        TrafficLight ns = new TrafficLight("NS", Set.of(north), new LightTiming(4, 1, 4), LightColor.RED);
        TrafficLight ew = new TrafficLight("EW", Set.of(east), new LightTiming(4, 1, 4), LightColor.GREEN);
        SignalNetwork signals = new SignalNetwork(List.of(ns, ew), List.of(new SignalNetwork.Pair(ns, ew)));

        List<Vehicle> fleet = List.of(
                new Vehicle(new VehicleId(0), n, s, 30, new Path(List.of(north), 1), 1, 1, 0, "A"),
                new Vehicle(new VehicleId(1), n, s, 30, new Path(List.of(north), 1), 1, 1, 0, "B"),
                new Vehicle(new VehicleId(2), n, s, 30, new Path(List.of(north), 1), 1, 1, 0, "C")
        );
        Simulation sim = new Simulation(new TrafficState(graph), signals, fleet, 90, null, false);
        int enteredBy = -1;
        for (int t = 0; t < 6; t++) {
            sim.step();
            long onMove = fleet.stream()
                    .filter(v -> !(v.position() instanceof VehiclePosition.AtNode))
                    .count();
            if (onMove > 0 && enteredBy < 0) {
                enteredBy = t + 1;
            }
        }
        assertTrue(enteredBy > 0 && enteredBy <= 3,
                "NS demand with empty EW should get green quickly; enteredBy=" + enteredBy);
        assertEquals(LightColor.GREEN, ns.color());
    }

    private Metrics runOneSided(SignalNetwork signals, int budget) {
        Scenario s = buildCross();
        List<Vehicle> fleet = new ArrayList<>();
        int id = 0;
        for (int i = 0; i < 6; i++) {
            fleet.add(car(id++, s.n(), s.s(), List.of(s.nc(), s.cs())));
            fleet.add(car(id++, s.s(), s.n(), List.of(s.sc(), s.cn())));
        }
        return run(s.graph(), signals, fleet, budget);
    }

    private Metrics runCompeting(SignalNetwork signals, int budget) {
        Scenario s = buildCross();
        List<Vehicle> fleet = new ArrayList<>();
        int id = 0;
        for (int i = 0; i < 8; i++) {
            fleet.add(car(id++, s.n(), s.s(), List.of(s.nc(), s.cs())));
            fleet.add(car(id++, s.s(), s.n(), List.of(s.sc(), s.cn())));
        }
        for (int i = 0; i < 2; i++) {
            fleet.add(car(id++, s.e(), s.w(), List.of(s.ec(), s.cw())));
            fleet.add(car(id++, s.w(), s.e(), List.of(s.wc(), s.ce())));
        }
        return run(s.graph(), signals, fleet, budget);
    }

    private Metrics run(RoadGraph graph, SignalNetwork signals, List<Vehicle> fleet, int budget) {
        Simulation sim = new Simulation(
                new TrafficState(graph),
                signals,
                fleet,
                fleet.size() * 40,
                null,
                false
        );
        int makespan = 0;
        for (int t = 0; t < budget; t++) {
            sim.step();
            makespan = t + 1;
            if (sim.allArrived()) {
                break;
            }
        }
        long totalTravel = 0;
        int queued = 0;
        for (Vehicle v : fleet) {
            if (v.arrived() && v.arrivedAtTick().isPresent()) {
                totalTravel += v.arrivedAtTick().get();
            } else {
                queued++;
                totalTravel += budget + 10;
            }
        }
        return new Metrics(makespan, totalTravel, queued);
    }

    private SignalNetwork policyNone() {
        return SignalNetwork.none();
    }

    private SignalNetwork policyFixed(int green, int yellow, int red) {
        Scenario s = buildCross();
        LightTiming timing = new LightTiming(green, yellow, red);
        TrafficLight ns = new TrafficLight("NS", s.nsEdges(), timing, LightColor.GREEN);
        TrafficLight ew = new TrafficLight("EW", s.ewEdges(), timing, LightColor.RED);
        return new SignalNetwork(
                List.of(ns, ew),
                List.of(new SignalNetwork.Pair(ns, ew)),
                SignalNetwork.ControlMode.FIXED_CYCLE
        );
    }

    private SignalNetwork policyFlowGuard(int green, int yellow, int red) {
        Scenario s = buildCross();
        LightTiming timing = new LightTiming(green, yellow, red);
        TrafficLight ns = new TrafficLight("NS", s.nsEdges(), timing, LightColor.GREEN);
        TrafficLight ew = new TrafficLight("EW", s.ewEdges(), timing, LightColor.RED);
        return new SignalNetwork(
                List.of(ns, ew),
                List.of(new SignalNetwork.Pair(ns, ew)),
                SignalNetwork.ControlMode.FLOW_GUARD
        );
    }

    private Scenario buildCross() {
        NodeId c = new NodeId(0);
        NodeId n = new NodeId(1);
        NodeId s = new NodeId(2);
        NodeId e = new NodeId(3);
        NodeId w = new NodeId(4);
        EdgeId cn = new EdgeId(0);
        EdgeId cs = new EdgeId(1);
        EdgeId ce = new EdgeId(2);
        EdgeId cw = new EdgeId(3);
        EdgeId nc = new EdgeId(4);
        EdgeId sc = new EdgeId(5);
        EdgeId ec = new EdgeId(6);
        EdgeId wc = new EdgeId(7);

        RoadGraph graph = new GraphBuilder()
                .addNode(c, "C").addNode(n, "N").addNode(s, "S").addNode(e, "E").addNode(w, "W")
                .addEdge(nc, n, c, 1, 1)
                .addEdge(sc, s, c, 1, 1)
                .addEdge(ec, e, c, 1, 1)
                .addEdge(wc, w, c, 1, 1)
                .addEdge(cn, c, n, 1, 2)
                .addEdge(cs, c, s, 1, 2)
                .addEdge(ce, c, e, 1, 2)
                .addEdge(cw, c, w, 1, 2)
                .build();

        return new Scenario(
                graph, Set.of(nc, sc), Set.of(ec, wc),
                n, s, e, w, c, nc, sc, ec, wc, cn, cs, ce, cw
        );
    }

    private static Vehicle car(int id, NodeId from, NodeId to, List<EdgeId> edges) {
        return new Vehicle(
                new VehicleId(id),
                from,
                to,
                120,
                new Path(edges, edges.size()),
                edges.size(),
                edges.size(),
                0,
                "C" + id
        );
    }

    private record Scenario(
            RoadGraph graph,
            Set<EdgeId> nsEdges,
            Set<EdgeId> ewEdges,
            NodeId n, NodeId s, NodeId e, NodeId w, NodeId c,
            EdgeId nc, EdgeId sc, EdgeId ec, EdgeId wc,
            EdgeId cn, EdgeId cs, EdgeId ce, EdgeId cw
    ) {
    }
}
