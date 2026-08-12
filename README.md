# CityFlow

**Priority traffic sandbox** — rehearse emergency and VIP corridor policy on a live city graph, then measure it against equal (Maps-like) routing.



CityFlow models intersections as nodes and roads as capacity-limited edges. Vehicles pathfind under congestion, signals, and accidents. The product question it answers:

> When an ambulance or VIP corridor runs through a busy network, does priority control get life-critical traffic through faster **without** unfairly punishing civilians?

| Mode | Behavior |
|------|----------|
| **CityFlow** | FIRE → AMBULANCE → POLICE → VIP → civilian; corridors, preemption, soft buffers |
| **Maps-like** | Congestion-aware routing only — no class privilege |

Compare both on the same blueprint (`Compare` in the UI).

## Run

```bash
# API
mvn -q exec:java -Dexec.mainClass=com.traffic.api.ApiMain

# UI (proxies /api → :8080)
cd web && npm install && npm run dev
```

```bash
mvn test
```

**Docker (API + built UI):**

```bash
docker build -t city-flow .
docker run --rm -p 8080:8080 -v cityflow-data:/app/data city-flow
```

## Stack

Java 17 simulation engine · React + Vite UI · JDK HTTP API · optional single-jar / Docker serve

## Engine notes

- Dijkstra / A\* with dynamic & priority edge costs  
- Parallel tick (advance + stripe departures) and dedicated routing pools  
- Lock-striped occupancy; lock-free corridor snapshots  
- Multi-session via `X-Session-Id`  
- Presets: Playground, Downtown, Megacity, Kolkata  

Config: [`.env.example`](.env.example). Health: `GET /api/health`.



