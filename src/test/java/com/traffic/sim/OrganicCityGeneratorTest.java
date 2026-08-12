package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.RoadGraph;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Router;
import com.traffic.routing.Routers;
import com.traffic.routing.RoutingAlgorithm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class OrganicCityGeneratorTest {

    @Test
    void buildsDenseIrregularConnectedCity() {
        EditableCity city = OrganicCityGenerator.generate(42L);
        assertTrue(city.nodeCount() > 80, "expected a busy fabric");
        assertTrue(city.edgeCount() > city.nodeCount(), "expected rich connectivity");

        RoadGraph graph = city.snapshot();
        Router router = Routers.create(RoutingAlgorithm.ASTAR, graph);
        List<Node> nodes = new ArrayList<>(city.nodes());
        Node start = nodes.get(0);
        for (int i = 1; i < nodes.size(); i += Math.max(1, nodes.size() / 25)) {
            Node goal = nodes.get(i);
            assertTrue(
                    router.findPath(graph, start.id(), goal.id(), EdgeCost.baseWeight()).isPresent(),
                    "disconnected: " + start.id() + " -> " + goal.id()
            );
        }
    }

    @Test
    void rushHourSamplesOnlyReachablePairs() {
        EditableCity city = OrganicCityGenerator.generate(99L);
        RoadGraph graph = city.snapshot();
        var trips = FleetFactory.commuteTrips(graph, 12, 7L);
        Router router = Routers.create(RoutingAlgorithm.DIJKSTRA, graph);
        for (var trip : trips) {
            assertTrue(
                    router.findPath(graph, trip.start(), trip.goal(), EdgeCost.baseWeight()).isPresent(),
                    "rush trip not reachable: " + trip.start() + " -> " + trip.goal()
            );
        }
    }
}
