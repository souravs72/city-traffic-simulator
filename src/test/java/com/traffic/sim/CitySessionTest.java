package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.Node;
import com.traffic.model.signal.LightTiming;
import com.traffic.routing.RoutingAlgorithm;

import org.junit.jupiter.api.Test;

class CitySessionTest {

    @Test
    void buildPlayApplyShortcutOnGrid() {
        SimConfig config = SimConfig.defaults();
        CitySession session = CitySession.openGrid(config, CityGenConfig.playground(), 4, 7L);

        assertEquals(SessionMode.BUILD, session.mode());
        assertThrows(IllegalStateException.class, session::step);

        Node a = byLabel(session, "R0C0");
        Node b = byLabel(session, "R2C2");
        session.city().connectOneWay(a.id(), b.id(), 2);
        assertTrue(session.hasUnappliedEdits());

        int replans = session.applyEdits();
        assertFalse(session.hasUnappliedEdits());
        assertTrue(replans >= 0);

        session.play();
        assertEquals(SessionMode.PLAY, session.mode());
        session.run(40);
        assertTrue(session.arrivedCount() >= 1);
    }

    @Test
    void downtownFleetMostlyArrives() {
        SimConfig config = new SimConfig(
                400,
                250,
                RoutingAlgorithm.DIJKSTRA,
                LightTiming.playful(),
                2,
                8,
                false
        );
        CitySession session = CitySession.openGrid(
                config,
                CityGenConfig.downtown(),
                8,
                99L
        );
        session.play();
        session.run(400);
        assertTrue(session.arrivedCount() >= 6,
                "expected most of the fleet to arrive, got " + session.arrivedCount());
    }

    private static Node byLabel(CitySession session, String label) {
        return session.city().nodes().stream()
                .filter(n -> n.label().equals(label))
                .findFirst()
                .orElseThrow();
    }
}
