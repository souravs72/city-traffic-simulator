package com.traffic.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.api.dto.SessionSnapshotDto;
import com.traffic.api.dto.TripRequest;
import com.traffic.persist.CityStore;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionHubTest {

    @TempDir
    Path tempDir;

    private SessionHub hub() {
        return new SessionHub(new CityStore(tempDir.resolve("city-flow.json")));
    }

    @Test
    void createPlayStepAndDrawRoad() {
        SessionHub hub = hub();
        SessionSnapshotDto created = hub.create(new CreateSessionRequest(null, 4, 4, 4, 1L, 120, 80));
        assertEquals("BUILD", created.mode());
        assertTrue(created.nodeCount() >= 16);
        assertTrue(created.fleetSize() >= 4);

        int beforeEdges = created.edgeCount();
        SessionSnapshotDto afterRoad = hub.connect(new ConnectEdgeRequest(0, 15, 2, false, "AVENUE"));
        assertTrue(afterRoad.edgeCount() > beforeEdges);
        assertTrue(afterRoad.hasUnappliedEdits());

        SessionSnapshotDto playing = hub.play();
        assertEquals("PLAY", playing.mode());
        assertTrue(!playing.hasUnappliedEdits());

        SessionSnapshotDto stepped = hub.step();
        assertTrue(stepped.worldTick() >= 1);
    }

    @Test
    void addTripRecordsShortestAndLiveTiming() {
        SessionHub hub = hub();
        hub.create(new CreateSessionRequest("PLAYGROUND", 0, 0, 0, 3L, 120, 80));
        SessionSnapshotDto after = hub.addTrip(new TripRequest(0, 8, null));
        assertEquals(1, after.fleetSize());
        var car = after.vehicles().get(0);
        assertEquals("A", car.name());
        assertEquals(0, car.origin());
        assertEquals(8, car.destination());
        assertTrue(car.plannedShortestTicks() > 0);
        assertTrue(car.plannedLiveTicks() >= car.plannedShortestTicks());
        assertEquals(4, car.plannedShortestTicks());
    }

    @Test
    void createFromDowntownPresetStartsEmpty() {
        SessionHub hub = hub();
        SessionSnapshotDto created = hub.create(
                new CreateSessionRequest("DOWNTOWN", 0, 0, 0, 7L, 0, 200));
        assertEquals("BUILD", created.mode());
        assertEquals(64, created.nodeCount());
        assertEquals(0, created.fleetSize());
    }

    @Test
    void deleteNodeAndRoad() {
        SessionHub hub = hub();
        hub.create(new CreateSessionRequest("BLANK", 0, 0, 0, 1L, 100, 100));
        hub.addNode(new com.traffic.api.dto.AddNodeRequest(40, 40, "A"));
        hub.addNode(new com.traffic.api.dto.AddNodeRequest(180, 40, "B"));
        SessionSnapshotDto linked = hub.connect(new ConnectEdgeRequest(0, 1, 2, true, "AVENUE"));
        assertEquals(2, linked.nodeCount());
        assertTrue(linked.edgeCount() >= 2);

        int edgeId = linked.edges().get(0).id();
        SessionSnapshotDto afterRoad = hub.removeEdge(new com.traffic.api.dto.IdRequest(edgeId));
        assertEquals(0, afterRoad.edgeCount());

        SessionSnapshotDto afterNode = hub.removeNode(new com.traffic.api.dto.IdRequest(0));
        assertEquals(1, afterNode.nodeCount());
    }

    @Test
    void createWithoutReplaceSavedRefusesToOverwriteExistingSave() throws Exception {
        Path save = tempDir.resolve("city-flow.json");
        SessionHub hub = new SessionHub(new CityStore(save));
        hub.create(new CreateSessionRequest("BLANK", 0, 0, 0, 1L, 100, 100, true));
        hub.addNode(new com.traffic.api.dto.AddNodeRequest(10, 10, "KeepMe"));
        assertTrue(Files.exists(save));
        long sizeBefore = Files.size(save);

        SessionHub other = new SessionHub(new CityStore(save));
        assertThrows(IllegalStateException.class, () ->
                other.create(new CreateSessionRequest("PLAYGROUND", 0, 0, 0, 2L, 100, 100, false)));

        assertEquals(sizeBefore, Files.size(save), "save file must remain untouched");
    }
}
