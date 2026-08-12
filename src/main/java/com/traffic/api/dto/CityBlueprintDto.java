package com.traffic.api.dto;

import java.util.List;

/** Serializable city + trips so sessions survive refresh / API restart. */
public record CityBlueprintDto(
        String preset,
        List<NodeDto> nodes,
        List<BlueprintEdgeDto> edges,
        List<BlueprintTripDto> trips,
        List<BlueprintAccidentDto> accidents,
        Integer schemaVersion
) {
    public CityBlueprintDto {
        if (schemaVersion == null || schemaVersion < 1) {
            schemaVersion = 1;
        }
    }

    /** Backward-compatible ctor for callers that omit schema version. */
    public CityBlueprintDto(
            String preset,
            List<NodeDto> nodes,
            List<BlueprintEdgeDto> edges,
            List<BlueprintTripDto> trips,
            List<BlueprintAccidentDto> accidents
    ) {
        this(preset, nodes, edges, trips, accidents, 1);
    }
}
