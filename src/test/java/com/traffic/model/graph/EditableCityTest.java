package com.traffic.model.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EditableCityTest {

    @Test
    void uiStyleEditThenSnapshot() {
        EditableCity city = new EditableCity();
        Node a = city.addIntersection(0, 0, "Plaza");
        Node b = city.addIntersection(5, 0, "Market");
        Edge road = city.connectOneWay(a.id(), b.id(), 3);

        assertEquals(5, road.baseWeight());
        assertEquals(2, city.nodeCount());
        assertEquals(1, city.edgeCount());

        List<CityChange> changes = city.drainChanges();
        assertEquals(3, changes.size());

        RoadGraph graph = city.snapshot();
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
        assertEquals(1, graph.outgoing(a.id()).size());
    }

    @Test
    void twoWayAndBulldoze() {
        EditableCity city = new EditableCity();
        Node a = city.addIntersection(0, 0);
        Node b = city.addIntersection(0, 4);
        city.drainChanges();

        city.connectTwoWay(a.id(), b.id(), 2);
        assertEquals(2, city.edgeCount());

        Edge ab = city.findEdge(a.id(), b.id()).orElseThrow();
        city.removeEdge(ab.id());
        assertEquals(1, city.edgeCount());
        assertTrue(city.findEdge(b.id(), a.id()).isPresent());

        List<CityChange> changes = city.drainChanges();
        assertTrue(changes.stream().anyMatch(c -> c instanceof CityChange.EdgeRemoved));
    }
}
