package com.traffic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.config.CityGenConfig;
import com.traffic.model.graph.Edge;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GridSignalPlannerTest {

    @Test
    void syncedRedCoversGreenPlusYellow() {
        LightTiming synced = GridSignalPlanner.syncedTiming(LightTiming.playful());
        assertEquals(
                LightTiming.playful().greenTicks() + LightTiming.playful().yellowTicks(),
                synced.redTicks()
        );
    }

    @Test
    void playgroundFlowGuardStartsAllClear() {
        RoadGraph graph = GridCityGenerator.generate(CityGenConfig.playground()).snapshot();
        SignalNetwork signals = GridSignalPlanner.forGraph(graph, LightTiming.playful());

        assertFalse(signals.lights().isEmpty());
        for (TrafficLight light : signals.lights()) {
            assertEquals(LightColor.GREEN, light.color(), light.name());
        }
    }

    @Test
    void fixedCycleOppositePhasesNeverBothGreen() {
        RoadGraph graph = GridCityGenerator.generate(CityGenConfig.playground()).snapshot();
        SignalNetwork flow = GridSignalPlanner.forGraph(graph, LightTiming.playful());
        // Rebuild as FIXED_CYCLE with alternating starts for exclusivity check.
        List<TrafficLight> lights = new ArrayList<>();
        List<SignalNetwork.Pair> pairs = new ArrayList<>();
        int i = 0;
        for (SignalNetwork.Pair pair : flow.pairs()) {
            boolean nsFirst = (i++ % 2 == 0);
            TrafficLight a = new TrafficLight(
                    pair.a().name(), pair.a().controlledEdges(),
                    pair.a().timing(), nsFirst ? LightColor.GREEN : LightColor.RED);
            TrafficLight b = new TrafficLight(
                    pair.b().name(), pair.b().controlledEdges(),
                    pair.b().timing(), nsFirst ? LightColor.RED : LightColor.GREEN);
            lights.add(a);
            lights.add(b);
            pairs.add(new SignalNetwork.Pair(a, b));
        }
        SignalNetwork signals = new SignalNetwork(lights, pairs, SignalNetwork.ControlMode.FIXED_CYCLE);

        for (int t = 0; t < 12; t++) {
            for (var node : graph.nodes()) {
                var out = graph.outgoing(node.id());
                if (out.size() < 2) {
                    continue;
                }
                for (Edge edge : out) {
                    if (signals.colorOf(edge.id()).orElse(null) != LightColor.GREEN) {
                        continue;
                    }
                    for (Edge other : out) {
                        if (other.id().equals(edge.id()) || isSameAxis(graph, edge, other)) {
                            continue;
                        }
                        assertTrue(
                                signals.colorOf(other.id()).orElse(LightColor.RED) != LightColor.GREEN,
                                "opposite approach also green at tick " + t
                        );
                    }
                }
            }
            signals.tick();
        }
    }

    private static boolean isSameAxis(RoadGraph graph, Edge a, Edge b) {
        return isVertical(graph, a) == isVertical(graph, b);
    }

    private static boolean isVertical(RoadGraph graph, Edge edge) {
        var from = graph.requireNode(edge.from());
        var to = graph.requireNode(edge.to());
        return Math.abs(to.y() - from.y()) >= Math.abs(to.x() - from.x());
    }
}
