package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.config.CityGenConfig;
import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.RoadGraph;
import com.traffic.routing.EdgeCost;
import com.traffic.routing.Path;
import com.traffic.routing.Routers;
import com.traffic.routing.RoutingAlgorithm;

import org.junit.jupiter.api.Test;

class GridCityGeneratorTest {

    @Test
    void buildsPlaygroundGrid() {
        EditableCity city = GridCityGenerator.generate(CityGenConfig.playground());
        assertEquals(9, city.nodeCount());
        assertEquals(24, city.edgeCount());

        RoadGraph graph = city.snapshot();
        assertEquals(9, graph.nodeCount());
    }

    @Test
    void downtownIsRoutableCornerToCorner() {
        EditableCity city = GridCityGenerator.generate(CityGenConfig.downtown());
        assertEquals(64, city.nodeCount());

        RoadGraph graph = city.snapshot();
        Node start = city.nodes().stream()
                .filter(n -> n.label().equals("R0C0"))
                .findFirst()
                .orElseThrow();
        Node goal = city.nodes().stream()
                .filter(n -> n.label().equals("R7C7"))
                .findFirst()
                .orElseThrow();

        Path path = Routers.create(RoutingAlgorithm.DIJKSTRA, graph)
                .findPath(graph, start.id(), goal.id(), EdgeCost.baseWeight())
                .orElseThrow();
        assertTrue(path.hopCount() > 0);
        assertTrue(path.totalCost() > 0);
    }

    @Test
    void userCanDrawShortcutOnGeneratedCity() {
        EditableCity city = GridCityGenerator.generate(CityGenConfig.playground());
        Node a = city.nodes().stream().filter(n -> n.label().equals("R0C0")).findFirst().orElseThrow();
        Node c = city.nodes().stream().filter(n -> n.label().equals("R2C2")).findFirst().orElseThrow();

        int before = city.edgeCount();
        city.connectOneWay(a.id(), c.id(), 2);
        assertEquals(before + 1, city.edgeCount());
        assertEquals(1, city.drainChanges().size());
    }
}
