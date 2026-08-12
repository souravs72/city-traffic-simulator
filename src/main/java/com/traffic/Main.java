package com.traffic;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.GraphBuilder;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;

/**
 *  Entry point. Keep this class thin: wire pieces together later,
 * do not put simulation logic here.
 * 
 */

public class Main { 

    private Main() { 
        // utility/entry class - not meant to be instantiated
    }

    public static void main(String[] args) { 
        RoadGraph g = new GraphBuilder()
            .addNode(new NodeId(0), "A")
            .addNode(new NodeId(1), "B")
            .addEdge(new EdgeId(0), new NodeId(0), new NodeId(1), 3, 2)
            .build();
        System.out.println(g.nodeCount() + " nodes, " + g.edgeCount() + " edges");
    }
}