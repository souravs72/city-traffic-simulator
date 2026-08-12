package com.traffic.sim;

import com.traffic.api.dto.CityBlueprintDto;
import com.traffic.api.dto.PolicyCompareDto;

/**
 * @deprecated Use {@link com.traffic.eval.PolicyArena} — kept as a binary-compatible shim.
 */
@Deprecated
public final class PolicyArena {

    private PolicyArena() {
    }

    public static PolicyCompareDto compare(CityBlueprintDto blueprint, int ticks) {
        return com.traffic.eval.PolicyArena.compare(blueprint, ticks);
    }
}
