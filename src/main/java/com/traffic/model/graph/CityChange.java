package com.traffic.model.graph;

import java.util.Objects;

/** Something the UI (or editor) did to the city map. */
public sealed interface CityChange
        permits CityChange.NodeAdded,
                CityChange.NodeRemoved,
                CityChange.EdgeAdded,
                CityChange.EdgeRemoved {

    record NodeAdded(Node node) implements CityChange {
        public NodeAdded {
            Objects.requireNonNull(node, "node");
        }
    }

    record NodeRemoved(NodeId nodeId) implements CityChange {
        public NodeRemoved {
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    record EdgeAdded(Edge edge) implements CityChange {
        public EdgeAdded {
            Objects.requireNonNull(edge, "edge");
        }
    }

    record EdgeRemoved(EdgeId edgeId, NodeId from, NodeId to) implements CityChange {
        public EdgeRemoved {
            Objects.requireNonNull(edgeId, "edgeId");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }
}
