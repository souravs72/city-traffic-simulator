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
    private final Map<Long, EdgeId> edgeByEndpoints = new HashMap<>();
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
        EdgeId id = edgeByEndpoints.get(endpointKey(from, to));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(edges.get(id));
    }

    /** Place an intersection (UI: click empty canvas). Auto-ids + auto label. */
    public Node addIntersection(double x, double y) {
        return addIntersection(x, y, "X" + nextNodeValue);
    }

    public Node addIntersection(double x, double y, String label) {
        return addIntersection(x, y, label, FacilityKind.NONE);
    }

    public Node addIntersection(double x, double y, String label, FacilityKind facility) {
        NodeId id = new NodeId(nextNodeValue++);
        Node node = new Node(id, label, x, y, facility == null ? FacilityKind.NONE : facility);
        nodes.put(id, node);
        outgoing.put(id, new ArrayList<>());
        version++;
        pendingChanges.add(new CityChange.NodeAdded(node));
        return node;
    }

    /** Designate (or clear) a facility on an existing intersection. */
    public Node setFacility(NodeId nodeId, FacilityKind facility) {
        Node existing = requireNode(nodeId);
        FacilityKind kind = facility == null ? FacilityKind.NONE : facility;
        Node updated = new Node(existing.id(), existing.label(), existing.x(), existing.y(), kind);
        nodes.put(nodeId, updated);
        version++;
        return updated;
    }

    /**
     * Draw a one-way street. Weight defaults from distance so long roads "feel" longer.
     * UI: drag from → to.
     */
    public Edge connectOneWay(NodeId from, NodeId to, int capacity) {
        return connectOneWay(from, to, RoadType.AVENUE.travelTicks(), capacity);
    }

    public Edge connectOneWay(NodeId from, NodeId to, RoadType roadType) {
        Objects.requireNonNull(roadType, "roadType");
        return connectOneWay(from, to, roadType.travelTicks(), roadType.capacity());
    }

    public Edge connectOneWay(NodeId from, NodeId to, int baseWeight, int capacity) {
        requireNode(from);
        requireNode(to);
        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot connect a node to itself");
        }
        if (findEdge(from, to).isPresent()) {
            throw new IllegalArgumentException("Road already exists " + from + "→" + to);
        }
        return addEdgeExplicit(from, to, baseWeight, capacity);
    }

    /** Two-way boulevard (UI: shift-drag or "two-way" toggle). */
    public List<Edge> connectTwoWay(NodeId from, NodeId to, int capacity) {
        return connectTwoWay(from, to, RoadType.AVENUE);
    }

    public List<Edge> connectTwoWay(NodeId from, NodeId to, RoadType roadType) {
        Objects.requireNonNull(roadType, "roadType");
        Edge forward = connectOneWay(from, to, roadType);
        Edge back = connectOneWay(to, from, roadType);
        return List.of(forward, back);
    }

    public Edge addEdgeExplicit(NodeId from, NodeId to, int baseWeight, int capacity) {
        requireNode(from);
        requireNode(to);
        EdgeId id = new EdgeId(nextEdgeValue++);
        Edge edge = new Edge(id, from, to, baseWeight, capacity);
        edges.put(id, edge);
        edgeByEndpoints.put(endpointKey(from, to), id);
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
        edgeByEndpoints.remove(endpointKey(edge.from(), edge.to()));
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
            builder.addNode(node.id(), node.label(), node.x(), node.y(), node.facility());
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

    private static long endpointKey(NodeId from, NodeId to) {
        return (((long) from.value()) << 32) | (to.value() & 0xffff_ffffL);
    }
}
