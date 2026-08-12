package com.traffic.routing;

/**
 * Chooses which shortest-path algorithm to use.
 * Prefer one algorithm per run; both are optimal under the usual conditions.
 */
public enum RoutingAlgorithm {
    DIJKSTRA,
    ASTAR
}
