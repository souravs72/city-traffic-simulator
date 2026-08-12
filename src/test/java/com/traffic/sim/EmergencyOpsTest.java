package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.config.SimConfig;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.routing.RoutingAlgorithm;
import com.traffic.model.signal.LightTiming;

import java.util.List;

import org.junit.jupiter.api.Test;

class EmergencyOpsTest {

    private static SimConfig cfg() {
        return new SimConfig(200, 200, RoutingAlgorithm.DIJKSTRA, LightTiming.playful(), 2, 8, false, 8);
    }

    @Test
    void megacitySeedsFacilitiesAndDispatchWorks() {
        CitySession session = CitySession.openOrganic(cfg(), 0, 99L);
        long hospitals = session.city().nodes().stream().filter(n -> n.facility() == FacilityKind.HOSPITAL).count();
        long police = session.city().nodes().stream().filter(n -> n.facility() == FacilityKind.POLICE_STATION).count();
        long fire = session.city().nodes().stream().filter(n -> n.facility() == FacilityKind.FIRE_STATION).count();
        assertTrue(hospitals >= 1, "need hospital");
        assertTrue(police >= 1, "need police");
        assertTrue(fire >= 1, "need fire");

        Node scene = session.city().nodes().stream()
                .filter(n -> n.facility() == FacilityKind.NONE)
                .findFirst()
                .orElseThrow();
        Vehicle ambulance = session.dispatch(ServiceClass.AMBULANCE, scene.id());
        assertEquals(ServiceClass.AMBULANCE, ambulance.serviceClass());
        Vehicle engine = session.dispatch(ServiceClass.FIRE, scene.id());
        assertEquals(ServiceClass.FIRE, engine.serviceClass());
    }

    @Test
    void vipConvoyArmsLockdownAndEscorts() {
        CitySession session = CitySession.openOrganic(cfg(), 0, 7L);
        session.setControlPolicy(ControlPolicy.CITY_FLOW);
        List<Node> nodes = List.copyOf(session.city().nodes());
        NodeId a = nodes.get(0).id();
        NodeId b = nodes.get(Math.min(40, nodes.size() - 1)).id();
        if (a.equals(b)) {
            b = nodes.get(1).id();
        }
        List<Vehicle> convoy = session.scheduleVipConvoy(a, b, 10, 2);
        assertEquals(3, convoy.size());
        assertEquals(ServiceClass.VIP, convoy.get(0).serviceClass());
        assertTrue(session.corridors().hasActive());
    }
}
