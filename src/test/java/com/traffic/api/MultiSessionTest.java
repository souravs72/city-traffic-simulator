package com.traffic.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.persist.CityStore;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiSessionTest {

    @TempDir
    Path temp;

    @Test
    void twoSessionsAreIsolated() {
        ApiConfig cfg = ApiConfig.localLab(8080, temp);
        SessionHub hub = new SessionHub(new CityStore(temp.resolve("city-flow.json")), cfg);

        hub.bind("alpha");
        hub.create(new CreateSessionRequest("PLAYGROUND", 0, 0, 0, 1L, 0, 100, true));
        int alphaNodes = hub.snapshot().nodeCount();

        hub.bind("beta");
        hub.create(new CreateSessionRequest("BLANK", 0, 0, 0, 2L, 0, 100, true));
        int betaNodes = hub.snapshot().nodeCount();

        assertNotEquals(alphaNodes, betaNodes);
        assertEquals(0, betaNodes);

        hub.bind("alpha");
        assertEquals(alphaNodes, hub.snapshot().nodeCount());
        org.junit.jupiter.api.Assertions.assertTrue(hub.sessionCount() >= 2);
    }
}
