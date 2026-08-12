package com.traffic.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.api.ApiConfig;
import com.traffic.api.dto.CityBlueprintDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Persistence atomicity + schemaVersion. Instruction: Implement these chagnes ensuring best practices */
class CityStoreTest {

    @TempDir
    Path temp;

    @Test
    void saveIsAtomicAndIncludesSchemaVersion() throws Exception {
        Path file = temp.resolve("city-flow.json");
        CityStore store = new CityStore(file);
        CityBlueprintDto bp = new CityBlueprintDto(
                "PLAYGROUND",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ApiConfig.SCHEMA_VERSION
        );
        store.save(bp);
        assertTrue(Files.isRegularFile(file));
        assertTrue(Files.notExists(file.resolveSibling("city-flow.json.tmp")));

        CityBlueprintDto loaded = store.load().orElseThrow();
        assertEquals("PLAYGROUND", loaded.preset());
        assertEquals(ApiConfig.SCHEMA_VERSION, loaded.schemaVersion());
    }
}
