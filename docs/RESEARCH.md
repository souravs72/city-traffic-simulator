# Research notes — CityFlow

## Thesis

CityFlow is a **reproducible microsimulation laboratory for route-based multi-class priority corridors**. It tests whether green-corridor style emergency/VIP control (hard spine locks, soft buffers, signal preemption, ranked departure) reduces emergency arrival latency versus equal congestion-aware (“Maps-like”) routing while keeping civilian travel within a fairness band (mean and tail).

This sits between intersection-only EVP studies and full SUMO stacks: the differentiator is a first-class **mechanism ablation + fairness** harness.

## Mechanisms (ablations)

| Flag | Effect |
|------|--------|
| `priorityDeparture` | Ranked departures at nodes |
| `signalPreemption` | Privileged cut through red when outranking conflict |
| `corridorBlocking` | Hard corridor reservations (VIP lockdown) |
| `softBufferRouting` | Soft edge multipliers + softer emergency congestion costs |

Profiles: `NONE`, singles, `FULL`, leave-one-out (`FULL_MINUS_*`). See `com.traffic.eval.MechanismProfile`.

## Metrics

Collected by `MetricsCollector` / `RunMetrics`:

- Travel time mean, p50 / p90 / p99 (fleet + per class)
- Emergency best / p90 (SLA-oriented)
- Civilian Jain fairness index
- Arrived / stranded, VHT, throughput per tick

Stranded vehicles are scored as `tickBudget + 10`.

## Reproduce

```bash
mvn -q test
mvn -q exec:java -Dexec.mainClass=com.traffic.eval.EvalMain -Dexec.args="--suite default"
mvn -q exec:java -Dexec.mainClass=com.traffic.eval.EvalMain -Dexec.args="--suite ablation"
mvn -q exec:java -Dexec.mainClass=com.traffic.eval.EvalMain -Dexec.args="--suite signals"
```

Outputs land in `results/<batchId>/`:
- `batch.json` — suite metadata
- `manifests.json` — all run manifests
- `runs.jsonl` — one compact JSON object per line
- `summary.csv` — flat table

Eval runs use **serial ticks**. Multi-seed suites rebuild **seed-dependent blueprints** (civilian OD shuffle / grid demand); do not loop seeds over a single fixed blueprint.

`EvalMain` prints FULL vs NONE aggregates **per scenario**, not mixed across scenarios.

## Scope / non-claims

Policy sandbox — not a calibrated digital twin. No field travel-time calibration without external data.

## Literature anchors

- Nelson & Bullock — EVP impact on signalized arterials
- FHWA — Traffic Signal Preemption for Emergency Vehicles (cross-cutting)
- SUMO / EmV prioritization studies — response-time ratios under background demand
- Route-based / green-corridor ATMS priority (industry EVP evolution)
