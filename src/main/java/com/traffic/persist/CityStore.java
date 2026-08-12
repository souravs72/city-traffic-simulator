package com.traffic.persist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.api.dto.CityBlueprintDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Disk persistence for the active city blueprint. */
public final class CityStore {

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public CityStore(Path file) {
        this.file = file;
    }

    public static CityStore defaultStore() {
        return new CityStore(Path.of("data", "city-flow.json"));
    }

    public Optional<CityBlueprintDto> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(file.toFile(), CityBlueprintDto.class));
        } catch (IOException ex) {
            System.err.println("Could not load city save: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public void save(CityBlueprintDto blueprint) {
        try {
            Files.createDirectories(file.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), blueprint);
        } catch (IOException ex) {
            System.err.println("Could not save city: " + ex.getMessage());
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            System.err.println("Could not clear city save: " + ex.getMessage());
        }
    }

    public Path path() {
        return file;
    }
}
