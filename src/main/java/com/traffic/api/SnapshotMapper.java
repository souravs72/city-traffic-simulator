package com.traffic.api;

import com.traffic.api.dto.AccidentDto;
import com.traffic.api.dto.EdgeDto;
import com.traffic.api.dto.NodeDto;
import com.traffic.api.dto.SessionSnapshotDto;
import com.traffic.api.dto.VehicleDto;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.Node;
import com.traffic.model.traffic.Accident;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.sim.CitySession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SnapshotMapper {

    private SnapshotMapper() {
    }

    static SessionSnapshotDto from(CitySession session) {
        List<NodeDto> nodes = new ArrayList<>();
        for (Node node : session.city().nodes()) {
            nodes.add(new NodeDto(node.id().value(), node.label(), node.x(), node.y()));
        }
        nodes.sort(Comparator.comparingInt(NodeDto::id));

        List<EdgeDto> edges = new ArrayList<>();
        for (Edge edge : session.city().edges()) {
            edges.add(new EdgeDto(
                    edge.id().value(),
                    edge.from().value(),
                    edge.to().value(),
                    edge.baseWeight(),
                    edge.capacity()
            ));
        }
        edges.sort(Comparator.comparingInt(EdgeDto::id));

        List<VehicleDto> vehicles = new ArrayList<>();
        for (Vehicle vehicle : session.fleet()) {
            vehicles.add(toVehicle(vehicle));
        }

        List<AccidentDto> accidents = new ArrayList<>();
        for (Accident accident : session.traffic().activeAccidents()) {
            accidents.add(new AccidentDto(
                    accident.id(),
                    accident.edgeId().value(),
                    accident.caption(),
                    accident.ticksRemaining(),
                    accident.showCross()
            ));
        }

        return new SessionSnapshotDto(
                session.mode().name(),
                session.worldTick(),
                session.hasUnappliedEdits(),
                session.city().nodeCount(),
                session.city().edgeCount(),
                session.arrivedCount(),
                session.fleet().size(),
                nodes,
                edges,
                vehicles,
                accidents
        );
    }

    private static VehicleDto toVehicle(Vehicle vehicle) {
        if (vehicle.position() instanceof VehiclePosition.AtNode at) {
            return new VehicleDto(
                    vehicle.id().value(),
                    vehicle.destination().value(),
                    vehicle.fuel(),
                    vehicle.fuelBurned(),
                    vehicle.arrived(),
                    vehicle.replanCount(),
                    "AT_NODE",
                    at.node().value(),
                    null,
                    null
            );
        }
        VehiclePosition.OnEdge on = (VehiclePosition.OnEdge) vehicle.position();
        return new VehicleDto(
                vehicle.id().value(),
                vehicle.destination().value(),
                vehicle.fuel(),
                vehicle.fuelBurned(),
                vehicle.arrived(),
                vehicle.replanCount(),
                "ON_EDGE",
                null,
                on.edge().value(),
                on.ticksRemaining()
        );
    }
}
