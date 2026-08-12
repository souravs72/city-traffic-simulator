package com.traffic.model.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds a RoadGraph, then freezes it. */
public final class GraphBuilder {

    private final Map<NodeId, Node> nodes = new HashMap<>();
    private final Map<EdgeId, Edge> edges = new HashMap<>();
    private final Map<NodeId, List<Edge>> outgoingEdges = new HashMap<>();

    public GraphBuilder addNode(NodeId id, String label) {
        return addNode(id, label, 0.0, 0.0);
    }

    public GraphBuilder addNode(NodeId id, String label, double x, double y) {
        Objects.requireNonNull(id, "id");
        if (nodes.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate node: " + id);
        }
        nodes.put(id, new Node(id, label, x, y));
        outgoingEdges.putIfAbsent(id, new ArrayList<>());
        return this;
    }

    public GraphBuilder addEdge(
            EdgeId id,
            NodeId from,
            NodeId to,
            int baseWeight,
            int capacity
    ) {
        Objects.requireNonNull(id, "id");
        if (edges.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate edge: " + id);
        }
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            throw new IllegalArgumentException("from/to must exist before adding edge");
        }
        Edge edge = new Edge(id, from, to, baseWeight, capacity);
        edges.put(id, edge);
        outgoingEdges.computeIfAbsent(from, k -> new ArrayList<>()).add(edge);
        return this;
    }

    public RoadGraph build() {
        return new RoadGraph(nodes, edges, outgoingEdges);
    }
}
