package com.traffic.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.api.dto.SessionSnapshotDto;

import org.junit.jupiter.api.Test;

class SessionHubTest {

    @Test
    void createPlayStepAndDrawRoad() {
        SessionHub hub = new SessionHub();
        SessionSnapshotDto created = hub.create(new CreateSessionRequest(4, 4, 4, 1L, 120, 80));
        assertEquals("BUILD", created.mode());
        assertTrue(created.nodeCount() >= 16);
        assertTrue(created.fleetSize() >= 4);

        int beforeEdges = created.edgeCount();
        SessionSnapshotDto afterRoad = hub.connect(new ConnectEdgeRequest(0, 15, 2, false));
        assertTrue(afterRoad.edgeCount() > beforeEdges);
        assertTrue(afterRoad.hasUnappliedEdits());

        SessionSnapshotDto playing = hub.play();
        assertEquals("PLAY", playing.mode());
        assertTrue(!playing.hasUnappliedEdits());

        SessionSnapshotDto stepped = hub.step();
        assertTrue(stepped.worldTick() >= 1);
    }
}
