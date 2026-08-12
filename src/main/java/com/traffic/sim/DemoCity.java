package com.traffic.sim;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.signal.LightColor;
import com.traffic.model.signal.LightTiming;
import com.traffic.model.signal.SignalNetwork;
import com.traffic.model.signal.TrafficLight;

import java.util.List;
import java.util.Set;

/**
 * Small playful city fixtures for demos and tests.
 * Layout (directed):
 * <pre>
 *   A --ab--> B --bc--> C
 *   |         |
 *   ac        bd--> D --dc--> C
 * </pre>
 */
public final class DemoCity {

    public final NodeId a = new NodeId(0);
    public final NodeId b = new NodeId(1);
    public final NodeId c = new NodeId(2);
    public final NodeId d = new NodeId(3);

    public final EdgeId ab = new EdgeId(0);
    public final EdgeId bc = new EdgeId(1);
    public final EdgeId ac = new EdgeId(2);
    public final EdgeId bd = new EdgeId(3);
    public final EdgeId dc = new EdgeId(4);

    public final RoadGraph graph;

    public DemoCity() {
        this.graph = new GraphBuilder()
                .addNode(a, "Plaza", 0, 0)
                .addNode(b, "Market", 4, 0)
                .addNode(c, "Harbor", 4, 4)
                .addNode(d, "Park", 0, 4)
                .addEdge(ab, a, b, 3, 2)
                .addEdge(bc, b, c, 3, 2)
                .addEdge(ac, a, c, 8, 1)
                .addEdge(bd, b, d, 3, 2)
                .addEdge(dc, d, c, 3, 2)
                .build();
    }

    public SignalNetwork defaultSignals(LightTiming timing) {
        return new SignalNetwork(List.of(
                new TrafficLight("Plaza-out", Set.of(ab, ac), timing, LightColor.GREEN),
                new TrafficLight("Market-out", Set.of(bc, bd), timing, LightColor.GREEN)
        ));
    }
}
