package com.traffic.api;

import com.traffic.api.dto.AccidentDto;
import com.traffic.api.dto.EdgeDto;
import com.traffic.api.dto.NodeDto;
import com.traffic.api.dto.SessionSnapshotDto;
import com.traffic.api.dto.VehicleDto;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.graph.RoadType;
import com.traffic.model.signal.LightColor;
import com.traffic.model.traffic.Accident;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.RouteEstimator;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.rules.DynamicEdgeCost;
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
            nodes.add(new NodeDto(node.id().value(), node.label(), node.x(), node.y(), node.facility().name()));
        }
        nodes.sort(Comparator.comparingInt(NodeDto::id));

        List<EdgeDto> edges = new ArrayList<>();
        for (Edge edge : session.city().edges()) {
            String light = session.simulation().signals().colorOf(edge.id())
                    .map(LightColor::name)
                    .orElse(null);
            int occ = session.traffic().occupancy(edge.id());
            RoadType type = RoadType.classify(edge.baseWeight(), edge.capacity());
            edges.add(new EdgeDto(
                    edge.id().value(),
                    edge.from().value(),
                    edge.to().value(),
                    edge.baseWeight(),
                    edge.capacity(),
                    occ,
                    type.name(),
                    light
            ));
        }
        edges.sort(Comparator.comparingInt(EdgeDto::id));

        RoadGraph graph = session.traffic().graph();
        Router router = Routers.create(session.config().routingAlgorithm(), graph);
        EdgeCost liveCost = new DynamicEdgeCost(session.traffic(), session.config().congestionPenaltyPerCar());
        EdgeCost shortestCost = EdgeCost.baseWeight();

        List<VehicleDto> vehicles = new ArrayList<>();
        for (Vehicle vehicle : session.fleet()) {
            vehicles.add(toVehicle(vehicle, graph, router, shortestCost, liveCost));
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
                session.controlPolicy().name(),
                nodes,
                edges,
                vehicles,
                accidents
        );
    }

    private static VehicleDto toVehicle(
            Vehicle vehicle,
            RoadGraph graph,
            Router router,
            EdgeCost shortestCost,
            EdgeCost liveCost
    ) {
        Integer nodeId = null;
        Integer edgeId = null;
        Integer ticksRemaining = null;
        String positionType;
        NodeId here = null;

        if (vehicle.position() instanceof VehiclePosition.AtNode at) {
            positionType = "AT_NODE";
            nodeId = at.node().value();
            here = at.node();
        } else {
            VehiclePosition.OnEdge on = (VehiclePosition.OnEdge) vehicle.position();
            positionType = "ON_EDGE";
            edgeId = on.edge().value();
            ticksRemaining = on.ticksRemaining();
            here = graph.requireEdge(on.edge()).to();
        }

        Integer remainingShortest = null;
        Integer remainingLive = null;
        if (!vehicle.arrived() && here != null) {
            int prefix = ticksRemaining == null ? 0 : ticksRemaining;
            remainingShortest = RouteEstimator.estimate(
                            graph, router, here, vehicle.destination(), shortestCost)
                    .map(e -> e.travelTicks() + prefix)
                    .orElse(null);
            remainingLive = RouteEstimator.estimate(
                            graph, router, here, vehicle.destination(), liveCost)
                    .map(e -> e.travelTicks() + prefix)
                    .orElse(null);
        }

        return new VehicleDto(
                vehicle.id().value(),
                vehicle.name(),
                vehicle.origin().value(),
                vehicle.destination().value(),
                vehicle.fuel(),
                vehicle.fuelBurned(),
                vehicle.arrived(),
                vehicle.replanCount(),
                positionType,
                nodeId,
                edgeId,
                ticksRemaining,
                vehicle.plannedShortestTicks(),
                vehicle.plannedLiveTicks(),
                vehicle.spawnedAtTick(),
                vehicle.arrivedAtTick().orElse(null),
                vehicle.actualTicks().orElse(null),
                remainingShortest,
                remainingLive,
                vehicle.serviceClass().name(),
                vehicle.scheduledDepartAtTick()
        );
    }
}
