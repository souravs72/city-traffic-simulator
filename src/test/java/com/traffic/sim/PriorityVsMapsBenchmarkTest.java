package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.priority.PriorityMechanisms;
import com.traffic.model.priority.VipLockdown;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehicleId;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Routers;
import com.traffic.rules.PriorityEdgeCost;
import com.traffic.rules.Replanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Strict A/B: CityFlow priority stack vs Google-Maps-like (congestion routing only).
 * Same topology, same fleet, same signal hardware — only the control policy differs.
 */
class PriorityVsMapsBenchmarkTest {

    private record Metrics(
            int emergencyArrival,
            long civilianTotal,
            double civilianAvg,
            int civiliansQueued,
            int makespan
    ) {
    }

    @Test
    void emergencyOnly_cityFlowBeatsMapsOnAmbulanceAndCivilians() {
        Metrics maps = runScenario(ControlPolicy.MAPS_LIKE, 100, false);
        Metrics flow = runScenario(ControlPolicy.CITY_FLOW, 100, false);

        System.out.printf(
                "Emergency-only — maps(emerg=%d civAvg=%.2f) cityFlow(emerg=%d civAvg=%.2f)%n",
                maps.emergencyArrival(), maps.civilianAvg(),
                flow.emergencyArrival(), flow.civilianAvg()
        );

        assertTrue(flow.emergencyArrival() > 0 && maps.emergencyArrival() > 0);
        assertTrue(
                flow.emergencyArrival() < maps.emergencyArrival(),
                "CityFlow ambulance must beat Maps-like; flow="
                        + flow.emergencyArrival() + " maps=" + maps.emergencyArrival()
        );
        // No VIP lockdown: CityFlow must not punish civilians vs Maps.
        assertTrue(
                flow.civilianAvg() <= maps.civilianAvg(),
                "Without VIP, CityFlow civilian avg must be <= Maps; flow="
                        + flow.civilianAvg() + " maps=" + maps.civilianAvg()
        );
        assertEquals(0, flow.civiliansQueued());
    }

    @Test
    void withVipLockdown_cityFlowStillWinsEmergencyAndKeepsCiviliansMoving() {
        Metrics maps = runScenario(ControlPolicy.MAPS_LIKE, 100, true);
        Metrics flow = runScenario(ControlPolicy.CITY_FLOW, 100, true);

        System.out.printf(
                "VIP lockdown — maps(emerg=%d civAvg=%.2f) cityFlow(emerg=%d civAvg=%.2f)%n",
                maps.emergencyArrival(), maps.civilianAvg(),
                flow.emergencyArrival(), flow.civilianAvg()
        );

        assertTrue(flow.emergencyArrival() < maps.emergencyArrival());
        assertEquals(0, flow.civiliansQueued(), "civilians must still clear under lockdown");
        // VIP exclusive lane has a real cost; keep it bounded and documented.
        assertTrue(
                flow.civilianAvg() <= maps.civilianAvg() * 1.25,
                "VIP lockdown civilian cost must stay within 25% of Maps; flow="
                        + flow.civilianAvg() + " maps=" + maps.civilianAvg()
        );
    }

    @Test
    void vipCorridorBlocksCiviliansButNotFire() {
        Scenario s = buildCity();
        CorridorBoard board = new CorridorBoard();
        // Reserve the direct VIP spine for VIP+ (civilians diverted)
        board.activate(new CorridorBoard.Corridor(
                "vip-1",
                ServiceClass.VIP,
                Set.of(s.spineA(), s.spineB()),
                0,
                50
        ));

        assertTrue(board.blocks(s.spineA(), ServiceClass.CIVILIAN, 5));
        assertTrue(board.blocks(s.spineA(), ServiceClass.CIVILIAN, 5));
        assertTrue(!board.blocks(s.spineA(), ServiceClass.FIRE, 5));
        assertTrue(!board.blocks(s.spineA(), ServiceClass.AMBULANCE, 5));
        assertTrue(!board.blocks(s.spineA(), ServiceClass.POLICE, 5));
        assertTrue(!board.blocks(s.spineA(), ServiceClass.VIP, 5));
    }

    @Test
    void priorityOrderIsFireThenAmbulanceThenPoliceThenVip() {
        assertTrue(ServiceClass.FIRE.outranks(ServiceClass.AMBULANCE));
        assertTrue(ServiceClass.AMBULANCE.outranks(ServiceClass.POLICE));
        assertTrue(ServiceClass.POLICE.outranks(ServiceClass.VIP));
        assertTrue(ServiceClass.VIP.outranks(ServiceClass.CIVILIAN));
        assertTrue(ServiceClass.FIRE.preemptsSignals());
        assertTrue(!ServiceClass.VIP.preemptsSignals());
    }

    private Metrics runScenario(ControlPolicy policy, int budget, boolean withVip) {
        Scenario s = buildCity();
        CorridorBoard corridors = new CorridorBoard();
        TrafficState traffic = new TrafficState(s.graph());
        SignalNetwork signals = s.signals();

        List<Vehicle> fleet = new ArrayList<>();
        int id = 0;

        // Civilians west→east via arterial (capacity 1) + south→north cross traffic
        for (int i = 0; i < 8; i++) {
            fleet.add(car(id++, s.west(), s.east(),
                    List.of(s.westToJ(), s.spineA(), s.spineB()), ServiceClass.CIVILIAN, 0));
        }
        for (int i = 0; i < 4; i++) {
            fleet.add(car(id++, s.south(), s.north(),
                    List.of(s.crossIn(), s.crossOut()), ServiceClass.CIVILIAN, 0));
        }

        // Ambulance hospital → scene through the junction (contested approaches)
        Vehicle ambulance = car(
                id++,
                s.hospital(),
                s.scene(),
                List.of(s.hospToJ(), s.jToScene()),
                ServiceClass.AMBULANCE,
                0
        );
        fleet.add(ambulance);

        if (withVip) {
            Vehicle vip = car(
                    id++,
                    s.vipSite(),
                    s.east(),
                    List.of(s.vipToJ(), s.spineA(), s.spineB()),
                    ServiceClass.VIP,
                    6
            );
            fleet.add(vip);
            if (policy.mechanisms().corridorBlocking()) {
                VipLockdown.Plan plan = VipLockdown.plan(
                        s.graph(), List.of(s.vipToJ(), s.spineA(), s.spineB()));
                corridors.activate(new CorridorBoard.Corridor(
                        "vip-run",
                        ServiceClass.VIP,
                        plan.hardClosed(),
                        plan.softBuffer(),
                        0,
                        40
                ));
                corridors.setCurrentTick(0);
            }
        }

        PriorityMechanisms mechanisms = policy.mechanisms();
        Function<Vehicle, EdgeCost> costs = (Vehicle v) -> new PriorityEdgeCost(
                traffic, corridors, v.serviceClass(), 2, mechanisms
        );
        Replanner replanner = new Replanner(
                Routers.create(com.traffic.routing.RoutingAlgorithm.DIJKSTRA, s.graph()),
                costs
        );
        for (Vehicle v : fleet) {
            if (v.serviceClass() == ServiceClass.CIVILIAN) {
                EdgeCost cost = new PriorityEdgeCost(
                        traffic, corridors, v.serviceClass(), 2, mechanisms);
                Path alt = Routers.create(com.traffic.routing.RoutingAlgorithm.DIJKSTRA, s.graph())
                        .findPath(s.graph(), v.origin(), v.destination(), cost)
                        .orElse(new Path(v.remainingEdgesView(), v.remainingEdgesView().size()));
                v.replaceRemainingPath(alt);
            }
        }

        Simulation sim = new Simulation(
                traffic, signals, fleet, fleet.size() * 80, replanner, false, 8, corridors, policy,
                mechanisms, false, null
        );

        int makespan = 0;
        for (int t = 0; t < budget; t++) {
            // Keep PriorityEdgeCost world tick coherent via corridor board; replan uses tick 0 cost
            // but Simulation.needsReplan + replanner still divert when corridor blocks next hop.
            sim.step();
            makespan = t + 1;
            if (sim.allArrived()) {
                break;
            }
        }

        int emerg = ambulance.arrivedAtTick().orElse(-1);
        long civTotal = 0;
        int civN = 0;
        int queued = 0;
        for (Vehicle v : fleet) {
            if (v.serviceClass() != ServiceClass.CIVILIAN) {
                continue;
            }
            civN++;
            if (v.arrived() && v.arrivedAtTick().isPresent()) {
                civTotal += v.arrivedAtTick().get();
            } else {
                queued++;
                civTotal += budget + 10;
            }
        }
        double avg = civN == 0 ? 0 : (double) civTotal / civN;
        return new Metrics(emerg, civTotal, avg, queued, makespan);
    }

    private static Vehicle car(
            int id,
            NodeId from,
            NodeId to,
            List<EdgeId> edges,
            ServiceClass sc,
            int depart
    ) {
        return new Vehicle(
                new VehicleId(id),
                from,
                to,
                200,
                new Path(edges, edges.size()),
                edges.size(),
                edges.size(),
                0,
                sc.displayName() + id,
                sc,
                depart
        );
    }

    /**
     * Compact city: hospital / VIP / conflicted arterial with a parallel detour.
     *
     * <pre>
     *  N
     *  |
     *  J ---- spine ---- E
     *  | \               ^
     *  |  detour --------|
     *  H                 scene
     *  V (vip)
     *  S
     * </pre>
     */
    private Scenario buildCity() {
        NodeId j = new NodeId(0);
        NodeId west = new NodeId(1); // unused label alias for hospital side approach naming
        NodeId east = new NodeId(2);
        NodeId north = new NodeId(3);
        NodeId south = new NodeId(4);
        NodeId hospital = new NodeId(5);
        NodeId scene = new NodeId(6);
        NodeId vipSite = new NodeId(7);
        NodeId mid = new NodeId(8); // detour mid

        // edges
        EdgeId spineA = new EdgeId(0); // J -> midEast
        EdgeId spineB = new EdgeId(1); // midEast -> E
        EdgeId crossIn = new EdgeId(2); // S -> J
        EdgeId crossOut = new EdgeId(3); // J -> N
        EdgeId hospToJ = new EdgeId(4);
        EdgeId jToScene = new EdgeId(5);
        EdgeId vipToJ = new EdgeId(6);
        EdgeId detour1 = new EdgeId(7); // J -> mid
        EdgeId detour2 = new EdgeId(8); // mid -> E
        EdgeId westToJ = new EdgeId(9); // W -> J (civilians start west)
        // Actually use west as civilian origin west of J
        NodeId w = west;

        NodeId midEast = new NodeId(9);
        EdgeId spineA2 = spineA;
        EdgeId spineB2 = spineB;

        RoadGraph graph = new GraphBuilder()
                .addNode(j, "J", 100, 100)
                .addNode(w, "W", 20, 100)
                .addNode(east, "E", 260, 100)
                .addNode(north, "N", 100, 20)
                .addNode(south, "S", 100, 180)
                .addNode(hospital, "HOSPITAL", 20, 140, com.traffic.model.graph.FacilityKind.HOSPITAL)
                .addNode(scene, "SCENE", 260, 140)
                .addNode(vipSite, "VIP", 20, 60, com.traffic.model.graph.FacilityKind.VIP_SITE)
                .addNode(mid, "DET", 180, 160)
                .addNode(midEast, "ME", 180, 100)
                .addEdge(westToJ, w, j, 1, 2)
                .addEdge(spineA2, j, midEast, 1, 1)
                .addEdge(spineB2, midEast, east, 1, 1)
                .addEdge(crossIn, south, j, 1, 1)
                .addEdge(crossOut, j, north, 1, 2)
                .addEdge(hospToJ, hospital, j, 1, 2)
                .addEdge(jToScene, j, scene, 1, 2)
                .addEdge(vipToJ, vipSite, j, 1, 2)
                .addEdge(detour1, j, mid, 2, 2)
                .addEdge(detour2, mid, east, 2, 2)
                .build();

        // Paired lights on conflicting approaches into J: west/hosp/vip share "EW" vs south "NS"
        // Simplify: control spine entry from W and cross from S
        TrafficLight ew = new TrafficLight(
                "EW", Set.of(westToJ, hospToJ, vipToJ), new LightTiming(3, 1, 3), LightColor.RED);
        TrafficLight ns = new TrafficLight(
                "NS", Set.of(crossIn), new LightTiming(3, 1, 3), LightColor.GREEN);
        SignalNetwork signals = new SignalNetwork(
                List.of(ew, ns), List.of(new SignalNetwork.Pair(ew, ns)));

        return new Scenario(
                graph, signals, w, east, north, south, hospital, scene, vipSite,
                westToJ, spineA2, spineB2, crossIn, crossOut, hospToJ, jToScene, vipToJ, detour1, detour2
        );
    }

    private record Scenario(
            RoadGraph graph,
            SignalNetwork signals,
            NodeId west,
            NodeId east,
            NodeId north,
            NodeId south,
            NodeId hospital,
            NodeId scene,
            NodeId vipSite,
            EdgeId westToJ,
            EdgeId spineA,
            EdgeId spineB,
            EdgeId crossIn,
            EdgeId crossOut,
            EdgeId hospToJ,
            EdgeId jToScene,
            EdgeId vipToJ,
            EdgeId detour1,
            EdgeId detour2
    ) {
    }
}
