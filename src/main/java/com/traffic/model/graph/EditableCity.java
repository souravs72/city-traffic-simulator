package com.traffic.model.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable city workspace for UI editing.
 * Sim/routing still use immutable {@link #snapshot()} {@link RoadGraph}s.
 * <p>
 * Intended UI gestures map cleanly:
 * <ul>
 *   <li>Click empty map → {@link #addIntersection}</li>
 *   <li>Drag node→node → {@link #connectOneWay} / {@link #connectTwoWay}</li>
 *   <li>Bulldoze road → {@link #removeEdge}</li>
 * </ul>
 */
public final class EditableCity {

    private final Map<NodeId, Node> nodes = new HashMap<>();
    private final Map<EdgeId, Edge> edges = new HashMap<>();
    private final Map<NodeId, List<EdgeId>> outgoing = new HashMap<>();
    private final List<CityChange> pendingChanges = new ArrayList<>();

    private int nextNodeValue;
    private int nextEdgeValue;
    private int version;

    public int version() {
        return version;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public Collection<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    public Collection<Edge> edges() {
        return List.copyOf(edges.values());
    }

    public Optional<Node> node(NodeId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Optional<Edge> edge(EdgeId id) {
        return Optional.ofNullable(edges.get(id));
    }

    public Optional<Edge> findEdge(NodeId from, NodeId to) {
        for (Edge edge : edges.values()) {
            if (edge.from().equals(from) && edge.to().equals(to)) {
                return Optional.of(edge);
            }
        }
        return Optional.empty();
    }

    /** Place an intersection (UI: click empty canvas). Auto-ids + auto label. */
    public Node addIntersection(double x, double y) {
        return addIntersection(x, y, "X" + nextNodeValue);
    }

    public Node addIntersection(double x, double y, String label) {
        NodeId id = new NodeId(nextNodeValue++);
        Node node = new Node(id, label, x, y);
        nodes.put(id, node);
        outgoing.put(id, new ArrayList<>());
        version++;
        pendingChanges.add(new CityChange.NodeAdded(node));
        return node;
    }

    /**
     * Draw a one-way street. Weight defaults from distance so long roads "feel" longer.
     * UI: drag from → to.
     */
    public Edge connectOneWay(NodeId from, NodeId to, int capacity) {
        Node a = requireNode(from);
        Node b = requireNode(to);
        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot connect a node to itself");
        }
        if (findEdge(from, to).isPresent()) {
            throw new IllegalArgumentException("Road already exists " + from + "→" + to);
        }
        int weight = Math.max(1, (int) Math.round(Math.hypot(a.x() - b.x(), a.y() - b.y())));
        return addEdgeExplicit(from, to, weight, capacity);
    }

    /** Two-way boulevard (UI: shift-drag or "two-way" toggle). */
    public List<Edge> connectTwoWay(NodeId from, NodeId to, int capacity) {
        Edge forward = connectOneWay(from, to, capacity);
        Edge back = connectOneWay(to, from, capacity);
        return List.of(forward, back);
    }

    public Edge addEdgeExplicit(NodeId from, NodeId to, int baseWeight, int capacity) {
        requireNode(from);
        requireNode(to);
        EdgeId id = new EdgeId(nextEdgeValue++);
        Edge edge = new Edge(id, from, to, baseWeight, capacity);
        edges.put(id, edge);
        outgoing.computeIfAbsent(from, k -> new ArrayList<>()).add(id);
        version++;
        pendingChanges.add(new CityChange.EdgeAdded(edge));
        return edge;
    }

    public void removeEdge(EdgeId edgeId) {
        Edge edge = edges.remove(edgeId);
        if (edge == null) {
            throw new IllegalArgumentException("Unknown edge: " + edgeId);
        }
        List<EdgeId> outs = outgoing.get(edge.from());
        if (outs != null) {
            outs.remove(edgeId);
        }
        version++;
        pendingChanges.add(new CityChange.EdgeRemoved(edgeId, edge.from(), edge.to()));
    }

    /** Remove intersection and every incident road (UI: delete key). */
    public void removeIntersection(NodeId nodeId) {
        requireNode(nodeId);
        List<EdgeId> toRemove = new ArrayList<>();
        for (Edge edge : edges.values()) {
            if (edge.from().equals(nodeId) || edge.to().equals(nodeId)) {
                toRemove.add(edge.id());
            }
        }
        for (EdgeId edgeId : toRemove) {
            removeEdge(edgeId);
        }
        nodes.remove(nodeId);
        outgoing.remove(nodeId);
        version++;
        pendingChanges.add(new CityChange.NodeRemoved(nodeId));
    }

    /** Freeze an immutable graph for routing / simulation. */
    public RoadGraph snapshot() {
        GraphBuilder builder = new GraphBuilder();
        for (Node node : nodes.values()) {
            builder.addNode(node.id(), node.label(), node.x(), node.y());
        }
        for (Edge edge : edges.values()) {
            builder.addEdge(edge.id(), edge.from(), edge.to(), edge.baseWeight(), edge.capacity());
        }
        return builder.build();
    }

    /** UI consumes these to update sprites; clears the queue. */
    public List<CityChange> drainChanges() {
        List<CityChange> copy = List.copyOf(pendingChanges);
        pendingChanges.clear();
        return copy;
    }

    private Node requireNode(NodeId id) {
        Node node = nodes.get(Objects.requireNonNull(id, "id"));
        if (node == null) {
            throw new IllegalArgumentException("Unknown node: " + id);
        }
        return node;
    }
}
