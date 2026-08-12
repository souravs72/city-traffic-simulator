# City Traffic Simulator

Learning project: model a city as a weighted directed graph and simulate many cars
moving concurrently each tick. Focus: graph algorithms (Dijkstra / A*), multithreading,
and correctness invariants.

## Stack

| Layer | Tech |
|-------|------|
| Engine | Java 17 + Maven (`src/main/java`) |
| UI | **React + TypeScript + Vite** (`web/`) — not JavaFX |
| Link | HTTP API on `:8080` (`com.traffic.api.ApiMain`) → React via Vite proxy |

## Goals

- Intersections = nodes, one-way roads = weighted edges
- Cars pathfind and occupy edges for `weight` ticks (capacity-limited)
- Congestion changes edge costs; lights and accidents alter access
- Invariants: edge capacity never exceeded; fuel ledger conserved
- Big editable grids; BUILD draws roads, PLAY runs traffic; accidents show ✕
- Parallel pathfinding for larger fleets; per-edge lock striping on occupancy
- UI presets: Playground / Downtown / Megacity
- Auto traffic lights on grids; click roads to select and spawn ✕ accidents

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
| `api` | HTTP JSON API for React |
| `invariant` | Capacity + fuel checks |
| `config` | Simulation + city-gen knobs |

## Build & run

**Engine (CLI demo)**

```bash
mvn test
mvn -q exec:java
```

**API (for React)**

```bash
mvn -q exec:java -Dexec.mainClass=com.traffic.api.ApiMain
```

**UI**

```bash
cd web
npm install
npm run dev
```

Open the Vite URL. `/api` is proxied to `localhost:8080`.

## Production-ish demo (optional)

Env knobs (see `.env.example`):

| Variable | Purpose |
|----------|---------|
| `CITYFLOW_PORT` / `PORT` | Listen port (default `8080`) |
| `CITYFLOW_DATA_DIR` | Save directory (default `data/`) |
| `CITYFLOW_CORS_ORIGINS` | Comma-separated allowed browser origins |
| `CITYFLOW_API_KEY` | Require `X-Api-Key` on session/API routes (empty = open lab) |
| `CITYFLOW_STATIC_DIR` | Serve a Vite `dist/` from the API process |
| `CITYFLOW_MAX_*` | Body / run ticks / grid / fleet clamps |

UI: set matching `VITE_API_KEY` in `web/.env` when the API key is enabled.

**One-process Docker**

```bash
docker build -t city-flow .
docker run --rm -p 8080:8080 -e CITYFLOW_API_KEY=changeme -v cityflow-data:/app/data city-flow
```

Or locally after `cd web && npm run build`:

```bash
CITYFLOW_STATIC_DIR=web/dist mvn -q -DskipTests package
java -jar target/city-traffic-simulator-0.1.0-SNAPSHOT.jar
```

Health: `GET /api/health` (public) returns uptime, session presence, and auth mode.
This remains a **single-operator** lab — not multi-tenant.


## Why CityFlow (best use case)

**CityFlow prioritizes life over VIP over commute.** Use it to rehearse emergency / VIP corridor
policy against a Maps-like baseline before real signal changes.

| Audience | Fit |
|----------|-----|
| Municipal / ops what-if | Accident, rush hour, ambulance vs VIP lockdown |
| DSA / concurrency portfolio | Parallel tick, lock striping, dedicated pools, A* |
| Teaching lab | Playground → Downtown → Megacity / Kolkata |

### Parallel engine

- Phase A (cars on edges) and stripe-grouped departures run on a dedicated tick pool
- Path replans use a dedicated routing pool (not the JVM common pool)
- `CorridorBoard` reads are lock-free snapshots
- Multi-session via `X-Session-Id` (each browser tab gets its own city)

### Presets

Playground, Downtown, Megacity, **Kolkata** (stylized districts: Dalhousie, Strand, Howrah, Salt Lake, Hooghly).

```bash
curl -s http://localhost:8080/api/health
curl -s -H 'X-Session-Id: demo-a' -X POST http://localhost:8080/api/session/new
```
