package com.traffic.api.dto;

public record AccidentDto(
        String id,
        int edgeId,
        String caption,
        int ticksRemaining,
        boolean showCross
) {
}
