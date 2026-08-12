package com.traffic.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.persist.CityStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** HTTP smoke tests for CORS/auth/health. Instruction: Implement these chagnes ensuring best practices */
class ApiServerGuardTest {

    @TempDir
    Path temp;

    private ApiServer server;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @BeforeEach
    void start() throws Exception {
        ApiConfig base = ApiConfig.localLab(8080, temp);
        ApiConfig cfg = ApiConfig.withApiKey(
                new ApiConfig(
                        0,
                        temp,
                        base.corsOrigins(),
                        Optional.empty(),
                        Optional.empty(),
                        4_096,
                        50,
                        100,
                        8,
                        8,
                        100,
                        10,
                        4
                ),
                "test-key"
        );
        server = new ApiServer(cfg, new SessionHub(new CityStore(cfg.savePath()), cfg));
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.port() + path;
    }

    @Test
    void healthIsPublicAndRich() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/health"))).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"ok\":true"));
        assertTrue(res.body().contains("uptimeMs"));
        assertTrue(res.body().contains("single-operator"));
        assertTrue(res.body().contains("\"authRequired\":true"));
    }

    @Test
    void sessionRequiresApiKey() throws Exception {
        HttpResponse<String> denied = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/session"))).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, denied.statusCode());

        HttpResponse<String> ok = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/session")))
                        .header("X-Api-Key", "test-key")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(404, ok.statusCode());
    }

    @Test
    void rejectsDisallowedOrigin() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(url("/api/health")))
                        .header("Origin", "https://evil.example")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, res.statusCode());
    }

    @Test
    void hubRejectsOversizedGrid() {
        ApiConfig cfg = ApiConfig.localLab(8080, temp);
        SessionHub hub = new SessionHub(new CityStore(cfg.savePath()), cfg);
        assertThrows(IllegalArgumentException.class, () ->
                hub.create(new CreateSessionRequest(null, 64, 64, 10, 1L, 100, 100)));
    }
}
