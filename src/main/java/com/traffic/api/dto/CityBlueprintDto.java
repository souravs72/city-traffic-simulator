package com.traffic.api.dto;

import java.util.List;

/** Serializable city + trips so sessions survive refresh / API restart. */
public record CityBlueprintDto(
        String preset,
        List<NodeDto> nodes,
        List<BlueprintEdgeDto> edges,
        List<BlueprintTripDto> trips,
        List<BlueprintAccidentDto> accidents
) {
}
