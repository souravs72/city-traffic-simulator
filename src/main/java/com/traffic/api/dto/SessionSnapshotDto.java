package com.traffic.api.dto;

import java.util.List;

public record SessionSnapshotDto(
        String mode,
        int worldTick,
        boolean hasUnappliedEdits,
        int nodeCount,
        int edgeCount,
        long arrivedCount,
        int fleetSize,
        String controlPolicy,
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        List<VehicleDto> vehicles,
        List<AccidentDto> accidents
) {
}
