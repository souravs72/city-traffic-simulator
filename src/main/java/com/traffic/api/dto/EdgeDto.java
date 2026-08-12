package com.traffic.api.dto;

/**
 * {@code corridorStatus}: CLEAR | LOCKED (hard VIP/emergency lane) | DISCOURAGED (soft buffer).
 * {@code jammed}: true when occupancy is at/near capacity — system diverts via live routing.
 */
public record EdgeDto(
        int id,
        int from,
        int to,
        int baseWeight,
        int capacity,
        int occupancy,
        String roadType,
        String lightColor,
        String corridorStatus,
        boolean jammed
) {
}
