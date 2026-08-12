package com.traffic.api;

import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.blueprint.CityBlueprints;
import com.traffic.sim.CitySession;

/** HTTP-facing alias for {@link CityBlueprints}. Callers: SessionHub. User: fix review findings. */
public final class BlueprintMapper {

    private BlueprintMapper() {
    }

    public static CityBlueprintDto toBlueprint(CitySession session, String preset) {
        return CityBlueprints.snapshot(session, preset);
    }

    public static CitySession fromBlueprint(CityBlueprintDto bp) {
        return CityBlueprints.restore(bp);
    }
}
