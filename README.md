# City Traffic Simulator

Learning project: model a city as a weighted directed graph and simulate many cars
moving concurrently each tick. Focus: graph algorithms (Dijkstra / A*), multithreading,
and correctness invariants.

## Goals

- Intersections = nodes, one-way roads = weighted edges
- Cars pathfind and occupy edges for `weight` ticks (capacity-limited)
- Congestion changes edge costs; lights and accidents alter access
- Invariants: edge capacity never exceeded; fuel ledger conserved

## Architecture (packages)

| Package | Role |
|---------|------|
| `model.graph` | Immutable map topology |
| `model.traffic` | Live occupancy, congestion, closures |
| `model.vehicle` | Cars (fuel, position, path) |
| `model.signal` | Traffic lights |
| `routing` | Pathfinding + cost model |
| `rules` | Congestion, fuel, accidents, replan policy |
| `sim` | Tick loop, thread pool, lock striping |
| `invariant` | Capacity + fuel checks |
| `config` | Simulation knobs |

## Build & run

Requirements: Java 17+, Maven 3.9+

```bash
mvn test
mvn -q exec:java