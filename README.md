# City Traffic Simulator

Learning project: model a city as a weighted directed graph and simulate many cars
moving concurrently each tick. Focus: graph algorithms (Dijkstra / A*), multithreading,
and correctness invariants.

## Stack

| Layer | Tech |
|-------|------|
| Engine | Java 17 + Maven (`src/main/java`) |
| UI | **React + TypeScript + Vite** (`web/`) — not JavaFX |
| Link | HTTP API next (React talks to `CitySession`) |

## Goals

- Intersections = nodes, one-way roads = weighted edges
- Cars pathfind and occupy edges for `weight` ticks (capacity-limited)
- Congestion changes edge costs; lights and accidents alter access
- Invariants: edge capacity never exceeded; fuel ledger conserved
- Big editable grids; BUILD draws roads, PLAY runs traffic; accidents show ✕

## Architecture (packages)

| Package | Role |
|---------|------|
| `model.graph` | Topology + `EditableCity` for UI edits |
| `model.traffic` | Live occupancy, accidents (✕) |
| `model.vehicle` | Cars (fuel, position, path) |
| `model.signal` | Traffic lights (G/Y/R) |
| `routing` | Pathfinding + cost model |
| `rules` | Congestion, accidents, replan |
| `sim` | Tick loop, `CitySession` Build/Play |
| `invariant` | Capacity + fuel checks |
| `config` | Simulation + city-gen knobs |

## Build & run

**Engine**

```bash
mvn test
mvn -q exec:java
```

**UI (preview, mock data until API exists)**

```bash
cd web
npm install
npm run dev
```
