package com.traffic.model.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable city topology: nodes + directed edges. */
public final class RoadGraph {

    private final Map<NodeId, Node> nodes;
    private final Map<EdgeId, Edge> edges;
    private final Map<NodeId, List<Edge>> outgoingEdges;

    RoadGraph(
            Map<NodeId, Node> nodes,
            Map<EdgeId, Edge> edges,
            Map<NodeId, List<Edge>> outgoingEdges) {
                
        this.nodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.edges = Map.copyOf(Objects.requireNonNull(edges, "edges"));

        // freeze adjacency lists too
        var frozen = new HashMap<NodeId, List<Edge>>();
        for (var entry : Objects.requireNonNull(outgoingEdges, "outgoingEdges").entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.outgoingEdges = Map.copyOf(frozen);
    }

    public Optional<Node> node(NodeId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Optional<Edge> edge(EdgeId id) {
        return Optional.ofNullable(edges.get(id));
    }

    public Node requireNode(NodeId id) {
        Node node = nodes.get(id);

        if (node == null) {
            throw new IllegalArgumentException("Unknow node: " + id);
        }
        return node;
    }

    public Edge requireEdge(EdgeId id) {
        Edge edge = edges.get(id);
    

        if (edge == null) {
            throw new IllegalArgumentException("Unknown edge: " + id);
        }
        return edge;
    }

    /** Outgoing roads from this intersection (empty if none) */
    public List<Edge> outgoing(NodeId from) {
        Objects.requireNonNull(from, "from");
        return outgoingEdges.getOrDefault(from, List.of());

    }

    public int nodeCount() {
        return nodes.size();
    }
    
    public int edgeCount() {
        return edges.size();
    }
    

    public Iterable<Node> nodes() { 
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Iterable<Edge> edges() {
        return Collections.unmodifiableCollection(edges.values());
    }


}
