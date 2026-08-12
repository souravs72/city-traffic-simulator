package com.traffic.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for env/default API limits. Instruction: Implement these chagnes ensuring best practices */
class ApiConfigTest {

    @TempDir
    Path temp;

    @Test
    void localLabDefaultsAllowViteOrigins() {
        ApiConfig cfg = ApiConfig.localLab(8080, temp);
        assertTrue(cfg.isOriginAllowed("http://localhost:5173"));
        assertTrue(cfg.isOriginAllowed("http://127.0.0.1:8080"));
        assertFalse(cfg.isOriginAllowed("https://evil.example"));
        assertFalse(cfg.authRequired());
        assertEquals(temp.resolve("city-flow.json"), cfg.savePath());
    }

    @Test
    void clampsAndValidatesCreateBudgets() {
        ApiConfig cfg = ApiConfig.localLab(8080, temp);
        assertEquals(200, cfg.clampRunTicks(10_000));
        assertEquals(40, cfg.clampRush(999));
        assertEquals(80, cfg.clampCompareTicks(0));
        assertThrows(IllegalArgumentException.class, () -> cfg.validateCreate(100, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> cfg.validateCreate(4, 4, 50_000));
        cfg.validateCreate(8, 8, 100);
    }

    @Test
    void withApiKeyEnablesAuth() {
        ApiConfig base = ApiConfig.localLab(9090, temp);
        ApiConfig locked = ApiConfig.withApiKey(base, "secret");
        assertTrue(locked.authRequired());
        assertEquals(Optional.of("secret"), locked.apiKey());
        assertEquals(Set.copyOf(base.corsOrigins()), locked.corsOrigins());
    }
}
