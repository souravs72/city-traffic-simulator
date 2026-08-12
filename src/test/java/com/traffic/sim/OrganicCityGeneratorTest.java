package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.RoadGraph;
import com.traffic.routing.EdgeCost;
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
        List<Node> nodes = new ArrayList<>(city.nodes());
        Node start = nodes.get(0);
        Node goal = nodes.get(nodes.size() / 2);
        assertTrue(
                Routers.create(RoutingAlgorithm.ASTAR, graph)
                        .findPath(graph, start.id(), goal.id(), EdgeCost.baseWeight())
                        .isPresent()
        );
    }
}
