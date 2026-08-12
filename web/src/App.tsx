import { useCallback, useEffect, useRef, useState } from "react";
import "./App.css";
import { CityMap, type MapTool } from "./components/CityMap";
import type { CityPreset, FacilityKind, RoadType, ServiceClass, SessionSnapshot } from "./types";
import * as api from "./api";
import type { PolicyCompare } from "./api";

const TICK_MS = 1000;

const PRESETS: { id: CityPreset; label: string }[] = [
  { id: "PLAYGROUND", label: "Playground" },
  { id: "DOWNTOWN", label: "Downtown" },
  { id: "MEGACITY", label: "Megacity" },
];

const TOOLS: { id: MapTool; label: string; hint: string }[] = [
  { id: "NODE", label: "Node", hint: "Pick facility type, then click empty map" },
  { id: "ROAD", label: "Road", hint: "Pick a road class, then click two nodes" },
  { id: "TRIP", label: "Car", hint: "Pick service class, then click start → end" },
  { id: "CRASH", label: "Crash", hint: "Click a road to close it — cars detour live" },
];

const ROAD_TYPES: { id: RoadType; label: string }[] = [
  { id: "HIGHWAY", label: "Highway" },
  { id: "AVENUE", label: "Avenue" },
  { id: "ALLEY", label: "Alley" },
];

const FACILITIES: { id: FacilityKind; label: string }[] = [
  { id: "NONE", label: "Plain" },
  { id: "HOSPITAL", label: "Hospital" },
  { id: "POLICE_STATION", label: "Police" },
  { id: "FIRE_STATION", label: "Fire" },
  { id: "VIP_SITE", label: "VIP" },
];

const SERVICE_CLASSES: { id: ServiceClass; label: string }[] = [
  { id: "CIVILIAN", label: "Civilian" },
  { id: "VIP", label: "VIP" },
  { id: "POLICE", label: "Police" },
  { id: "AMBULANCE", label: "Ambulance" },
  { id: "FIRE", label: "Fire" },
];

function nodeLabel(session: SessionSnapshot, id: number): string {
  return session.nodes.find((n) => n.id === id)?.label ?? String(id);
}

export default function App() {
  const [session, setSession] = useState<SessionSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [linkFrom, setLinkFrom] = useState<number | null>(null);
  const [tripFrom, setTripFrom] = useState<number | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<number | null>(null);
  const [preset, setPreset] = useState<CityPreset>("PLAYGROUND");
  const [tool, setTool] = useState<MapTool>("NODE");
  const [clockRunning, setClockRunning] = useState(false);
  const [showLights, setShowLights] = useState(false);
  const [roadType, setRoadType] = useState<RoadType>("AVENUE");
  const [facilityKind, setFacilityKind] = useState<FacilityKind>("NONE");
  const [serviceClass, setServiceClass] = useState<ServiceClass>("CIVILIAN");
  const [vipDepartTick, setVipDepartTick] = useState(8);
  const [compareResult, setCompareResult] = useState<PolicyCompare | null>(null);
  const [dispatchScene, setDispatchScene] = useState<number | null>(null);
  const [tickAnimAt, setTickAnimAt] = useState(0);
  const stepping = useRef(false);

  const apply = useCallback((s: SessionSnapshot) => {
    setSession(s);
    setError(null);
  }, []);

  const applyTick = useCallback((s: SessionSnapshot) => {
    setSession(s);
    setError(null);
    setTickAnimAt(performance.now());
  }, []);

  function clearSelection() {
    setSelectedNodeId(null);
    setSelectedEdgeId(null);
    setLinkFrom(null);
    setTripFrom(null);
  }

  async function boot(nextPreset: CityPreset = preset, replaceSaved = false) {
    if (session && !replaceSaved) {
      const ok = window.confirm(
        `Replace your saved city with ${nextPreset}? This overwrites auto-save.`,
      );
      if (!ok) return;
      replaceSaved = true;
    }
    setClockRunning(false);
    setBusy(true);
    try {
      apply(
        await api.createSession({
          preset: nextPreset,
          seed: 42,
          fleetSize: 0,
          replaceSaved: replaceSaved || !session,
        }),
      );
      setPreset(nextPreset);
      clearSelection();
      setTool("NODE");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to create session");
    } finally {
      setBusy(false);
    }
  }

  async function startNewCity() {
    if (!window.confirm("Start a new Playground city? Your current map will be replaced.")) {
      return;
    }
    setClockRunning(false);
    setBusy(true);
    try {
      apply(await api.newCity());
      setPreset("PLAYGROUND");
      clearSelection();
      setTool("NODE");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start a new city");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void (async () => {
      setBusy(true);
      try {
        const existing = await api.getSession();
        apply(existing);
        setError(null);
      } catch {
        await boot("PLAYGROUND", true);
      } finally {
        setBusy(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function run(action: () => Promise<SessionSnapshot>) {
    setBusy(true);
    try {
      apply(await action());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!clockRunning) return;
    const id = window.setInterval(() => {
      if (stepping.current) return;
      stepping.current = true;
      void api
        .stepTick()
        .then((s) => {
          applyTick(s);
          if (s.arrivedCount >= s.fleetSize && s.fleetSize > 0) {
            setClockRunning(false);
          }
        })
        .catch((e) => {
          setError(e instanceof Error ? e.message : "Clock step failed");
          setClockRunning(false);
        })
        .finally(() => {
          stepping.current = false;
        });
    }, TICK_MS);
    return () => window.clearInterval(id);
  }, [clockRunning, applyTick]);

  async function deleteSelection() {
    if (clockRunning || busy) return;
    if (selectedEdgeId != null) {
      const id = selectedEdgeId;
      clearSelection();
      await run(() => api.deleteEdge(id));
      return;
    }
    if (selectedNodeId != null) {
      const id = selectedNodeId;
      clearSelection();
      await run(() => api.deleteNode(id));
    }
  }

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Delete" || e.key === "Backspace") {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault();
        void deleteSelection();
      }
      if (e.key === "Escape") {
        clearSelection();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEdgeId, selectedNodeId, clockRunning, busy]);

  async function startClock() {
    clearSelection();
    setBusy(true);
    try {
      await api.playMode();
      // Step once immediately so cars enter the first road and start sliding.
      applyTick(await api.stepTick());
      setClockRunning(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start clock");
    } finally {
      setBusy(false);
    }
  }

  function pauseClock() {
    setClockRunning(false);
    void run(api.buildMode);
  }

  async function onAddNode(x: number, y: number) {
    if (clockRunning) return;
    if (session?.mode === "PLAY") await run(api.buildMode);
    clearSelection();
    await run(() => api.addNode({ x, y, facility: facilityKind }));
  }

  async function onSelectNode(id: number) {
    if (!session || clockRunning) return;
    setSelectedEdgeId(null);

    if (tool === "NODE") {
      setSelectedNodeId((prev) => (prev === id ? null : id));
      setDispatchScene(id);
      setLinkFrom(null);
      setTripFrom(null);
      return;
    }

    if (tool === "TRIP") {
      setSelectedNodeId(null);
      setLinkFrom(null);
      if (tripFrom == null) {
        setTripFrom(id);
        setError(null);
        return;
      }
      if (tripFrom === id) {
        setTripFrom(null);
        return;
      }
      const from = tripFrom;
      setTripFrom(null);
      await run(() =>
        api.addTrip(
          from,
          id,
          undefined,
          serviceClass,
          serviceClass === "VIP" ? vipDepartTick : 0,
        ),
      );
      return;
    }

    if (tool === "CRASH") {
      return;
    }

    // ROAD
    if (session.mode === "PLAY") await run(api.buildMode);
    setTripFrom(null);
    setSelectedNodeId(null);
    if (linkFrom == null) {
      setLinkFrom(id);
      return;
    }
    if (linkFrom === id) {
      setLinkFrom(null);
      return;
    }
    const from = linkFrom;
    setLinkFrom(null);
    await run(() => api.connectEdge({ from, to: id, twoWay: true, roadType }));
  }

  async function onSelectEdge(id: number) {
    setLinkFrom(null);
    setTripFrom(null);
    setSelectedNodeId(null);
    if (tool === "CRASH") {
      await run(() => api.spawnAccident({ edgeId: id, durationTicks: 25 }));
      return;
    }
    if (clockRunning) return;
    setSelectedEdgeId((prev) => (prev === id ? null : id));
  }

  if (!session) {
    return (
      <div className="app">
        <div className="boot">
          <p className="eyebrow">City Traffic</p>
          <h1>Connecting…</h1>
          <p className="mute">{error ?? "Starting Java session"}</p>
          <button type="button" className="btn primary" onClick={() => void boot()} disabled={busy}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  const fleet = [...session.vehicles].sort((a, b) => a.id - b.id);
  const canRace = session.fleetSize > 0 && session.edgeCount > 0;
  const hasSelection = selectedNodeId != null || selectedEdgeId != null;
  const toolHint = TOOLS.find((t) => t.id === tool)?.hint ?? "";
  const signalsVisible = clockRunning || showLights;
  const hasAnyLights = session.edges.some((e) => e.lightColor != null);

  return (
    <div className={`app ${clockRunning ? "racing" : ""}`}>
      <header className="top">
        <div className="top-brand">
          <p className="eyebrow">Traffic lab</p>
          <h1>City Flow</h1>
        </div>
        <div className="top-meta">
          <span className="chip">{session.nodeCount} nodes</span>
          <span className="chip">{session.edgeCount} roads</span>
          <span className="chip">{session.fleetSize} cars</span>
          <span className={`chip ${clockRunning ? "live" : ""}`}>
            {clockRunning ? `t ${session.worldTick}s` : "build"}
          </span>
        </div>
      </header>

      {error && <div className="banner">{error}</div>}

      <div className="workspace">
        <section className="stage">
          <div className="toolbar">
            <div className="tool-group" role="tablist" aria-label="Draw tools">
              {TOOLS.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  role="tab"
                  className={`tool ${tool === t.id ? "on" : ""}`}
                  disabled={busy || (clockRunning && t.id !== "CRASH")}
                  onClick={() => {
                    setTool(t.id);
                    setLinkFrom(null);
                    setTripFrom(null);
                    setSelectedNodeId(null);
                  }}
                >
                  {t.label}
                </button>
              ))}
            </div>
            {!clockRunning && tool === "ROAD" && (
              <div className="road-type-group" role="group" aria-label="Road class">
                {ROAD_TYPES.map((r) => (
                  <button
                    key={r.id}
                    type="button"
                    className={`tool ${roadType === r.id ? "on" : ""}`}
                    disabled={busy}
                    onClick={() => setRoadType(r.id)}
                  >
                    {r.label}
                  </button>
                ))}
              </div>
            )}
            {!clockRunning && tool === "NODE" && (
              <div className="road-type-group" role="group" aria-label="Facility">
                {FACILITIES.map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    className={`tool ${facilityKind === f.id ? "on" : ""}`}
                    disabled={busy}
                    onClick={() => setFacilityKind(f.id)}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            )}
            {!clockRunning && tool === "TRIP" && (
              <div className="road-type-group" role="group" aria-label="Service class">
                {SERVICE_CLASSES.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    className={`tool ${serviceClass === s.id ? "on" : ""}`}
                    disabled={busy}
                    onClick={() => setServiceClass(s.id)}
                  >
                    {s.label}
                  </button>
                ))}
                {serviceClass === "VIP" && (
                  <div className="vip-depart" role="group" aria-label="VIP departure tick">
                    <span className="vip-depart-badge" aria-hidden>
                      VIP
                    </span>
                    <div className="vip-depart-body">
                      <span className="vip-depart-label">Depart</span>
                      <div className="vip-depart-stepper">
                        <button
                          type="button"
                          className="vip-depart-btn"
                          disabled={busy || vipDepartTick <= 0}
                          aria-label="Earlier departure"
                          onClick={() => setVipDepartTick((t) => Math.max(0, t - 1))}
                        >
                          −
                        </button>
                        <label className="vip-depart-field">
                          <span className="vip-depart-at">t</span>
                          <input
                            type="number"
                            min={0}
                            max={999}
                            value={vipDepartTick}
                            disabled={busy}
                            onChange={(e) =>
                              setVipDepartTick(Math.max(0, Number(e.target.value) || 0))
                            }
                          />
                        </label>
                        <button
                          type="button"
                          className="vip-depart-btn"
                          disabled={busy}
                          aria-label="Later departure"
                          onClick={() => setVipDepartTick((t) => t + 1)}
                        >
                          +
                        </button>
                      </div>
                    </div>
                    <span className="vip-depart-hint">locks corridor early</span>
                  </div>
                )}
              </div>
            )}
            {!clockRunning && tool !== "TRIP" && tool !== "ROAD" && tool !== "NODE" && (
              <p className="tool-hint">{toolHint}</p>
            )}
            {clockRunning && (
              <p className="tool-hint live-hint">
                Live · {(session.controlPolicy ?? "CITY_FLOW") === "MAPS_LIKE" ? "Maps-like" : "CityFlow"} ·
                {" "}locks divert civilians · capacity jams force detours · cars ease between ticks
              </p>
            )}
            <div className="tool-actions">
              <button
                type="button"
                className="btn ghost"
                disabled={busy || clockRunning || session.edgeCount < 2}
                onClick={() => void run(() => api.addRushHour(10))}
                title="Spawn commute trips: rim → center"
              >
                Rush hour
              </button>
              {hasAnyLights && (
                <button
                  type="button"
                  className={`btn ghost ${signalsVisible ? "on" : ""}`}
                  disabled={busy}
                  onClick={() => setShowLights((v) => !v)}
                  title={clockRunning ? "Lights stay on during the race" : "Show junction lights"}
                >
                  Lights {signalsVisible ? "on" : "off"}
                </button>
              )}
              <button
                type="button"
                className="btn danger"
                disabled={busy || clockRunning || !hasSelection}
                onClick={() => void deleteSelection()}
                title="Delete selected (Delete / Backspace)"
              >
                Delete
              </button>
              {!clockRunning ? (
                <button
                  type="button"
                  className="btn primary"
                  disabled={busy || !canRace}
                  onClick={() => void startClock()}
                >
                  Start race
                </button>
              ) : (
                <button type="button" className="btn" onClick={pauseClock}>
                  Pause
                </button>
              )}
            </div>
          </div>

          <CityMap
            tool={tool}
            nodes={session.nodes}
            edges={session.edges}
            accidents={session.accidents}
            vehicles={session.vehicles}
            selectedFrom={linkFrom}
            tripFrom={tripFrom}
            selectedNodeId={selectedNodeId}
            selectedEdgeId={selectedEdgeId}
            showSignals={signalsVisible}
            animateTravel={clockRunning}
            animStartedAt={tickAnimAt}
            tickDurationMs={TICK_MS}
            onSelectNode={(id) => void onSelectNode(id)}
            onSelectEdge={onSelectEdge}
            onAddNode={(x, y) => void onAddNode(x, y)}
          />

          <div className="stage-foot">
            <div className="legend" aria-label="Map legend">
              <span>
                <i className="swatch hwy" /> highway
              </span>
              <span>
                <i className="swatch ave" /> avenue
              </span>
              <span>
                <i className="swatch alley" /> alley
              </span>
              <span>
                <i className="mark car" /> cars
              </span>
              <span>
                <i className="dot heat" /> jam heat
              </span>
              <span>
                <i className="swatch lock" /> VIP/emergency lock
              </span>
              <span>
                <i className="swatch soft" /> soft buffer
              </span>
              <span>
                <i className="swatch detour" /> civilian detour
              </span>
              {signalsVisible && (
                <>
                  <span>
                    <i className="mark signal go" /> go
                  </span>
                  <span>
                    <i className="mark signal wait" /> stop
                  </span>
                </>
              )}
            </div>
            {(session.corridorActive || (session.jammedEdgeCount ?? 0) > 0) && (
              <p className="tool-hint live-hint" style={{ marginTop: 6 }}>
                {session.corridorActive
                  ? "Purple LOCK = closed to civilians · teal dashed = open detours · jams auto-reroute via capacity"
                  : `${session.jammedEdgeCount} jammed road(s) — cars replan around full capacity`}
              </p>
            )}
            {hasSelection && (
              <div className="selection-inline">
                {selectedNodeId != null && (
                  <span>
                    Node <strong>{nodeLabel(session, selectedNodeId)}</strong>
                  </span>
                )}
                {selectedEdgeId != null && (
                  <span>
                    Road <strong>#{selectedEdgeId}</strong>
                  </span>
                )}
                <span className="mute">Delete to remove</span>
              </div>
            )}
          </div>
        </section>

        <aside className="rail">
          <div className="card">
            <h2>Map</h2>
            <div className="seg">
              {PRESETS.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  className={preset === p.id ? "on" : ""}
                  disabled={busy || clockRunning}
                  onClick={() => void boot(p.id)}
                >
                  {p.label}
                </button>
              ))}
            </div>
            <button
              type="button"
              className="btn new-city"
              disabled={busy || clockRunning}
              onClick={() => void startNewCity()}
            >
              New city
            </button>
            <p className="hint">Auto-saves until you confirm a preset replace. New city opens Playground.</p>
            <div className="seg" style={{ marginTop: 10 }}>
              <button
                type="button"
                className={(session.controlPolicy ?? "CITY_FLOW") === "CITY_FLOW" ? "on" : ""}
                disabled={busy || clockRunning}
                onClick={() => void run(() => api.setPolicy("CITY_FLOW"))}
                title="FIRE > AMBULANCE > POLICE > VIP > civilian with corridors + preemption"
              >
                CityFlow
              </button>
              <button
                type="button"
                className={(session.controlPolicy ?? "CITY_FLOW") === "MAPS_LIKE" ? "on" : ""}
                disabled={busy || clockRunning}
                onClick={() => void run(() => api.setPolicy("MAPS_LIKE"))}
                title="Congestion routing only — no emergency privilege (Google-Maps-class)"
              >
                Maps-like
              </button>
            </div>
            <p className="hint">Compare CityFlow priority vs Maps-like equal routing.</p>
          </div>

          <details className="card help">
            <summary>How to play</summary>
            <ol className="steps">
              <li>Seed or mark hospitals / police / fire / VIP sites</li>
              <li>Dispatch emergency units to a selected scene node</li>
              <li>VIP convoy locks a corridor; civilians divert automatically</li>
              <li>Run Maps vs CityFlow for a strict fairness report</li>
            </ol>
          </details>


          <div className="card">
            <h2>Emergency ops</h2>
            <p className="hint">Select a scene node, then dispatch. Seed facilities if the map has none.</p>
            <div className="seg">
              <button type="button" disabled={busy || clockRunning || dispatchScene == null}
                onClick={() => void run(() => api.dispatchEmergency("AMBULANCE", dispatchScene!))}>Ambulance</button>
              <button type="button" disabled={busy || clockRunning || dispatchScene == null}
                onClick={() => void run(() => api.dispatchEmergency("FIRE", dispatchScene!))}>Fire</button>
              <button type="button" disabled={busy || clockRunning || dispatchScene == null}
                onClick={() => void run(() => api.dispatchEmergency("POLICE", dispatchScene!))}>Police</button>
            </div>
            <button
              type="button"
              className="btn ghost"
              style={{ marginTop: 8, width: "100%" }}
              disabled={busy || clockRunning || tripFrom == null || selectedNodeId == null || tripFrom === selectedNodeId}
              onClick={() => {
                if (tripFrom == null || selectedNodeId == null) return;
                void run(() =>
                  api.vipConvoy({
                    from: tripFrom,
                    to: selectedNodeId,
                    departAtTick: vipDepartTick,
                    escorts: 2,
                  }),
                );
              }}
              title="Use Car tool: click VIP start, then select end node, then convoy"
            >
              VIP convoy (start→selected)
            </button>
            <button
              type="button"
              className="btn ghost"
              style={{ marginTop: 8, width: "100%" }}
              disabled={busy || clockRunning}
              onClick={() => void run(() => api.seedFacilities())}
            >
              Seed facilities
            </button>
            <button
              type="button"
              className="btn primary"
              style={{ marginTop: 8, width: "100%" }}
              disabled={busy || clockRunning || session.fleetSize < 1}
              onClick={() => {
                void (async () => {
                  setBusy(true);
                  try {
                    const result = await api.comparePolicies(80);
                    setCompareResult(result);
                    setError(null);
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Compare failed");
                  } finally {
                    setBusy(false);
                  }
                })();
              }}
            >
              Run Maps vs CityFlow
            </button>
            {compareResult && (
              <div className="hint" style={{ marginTop: 10 }}>
                <div><strong>Verdict:</strong> {compareResult.verdict}</div>
                <div className="mono" style={{ marginTop: 6 }}>
                  Maps emerg {compareResult.mapsLike.emergencyArrivalTicks}s · civ avg {compareResult.mapsLike.civilianAvgTicks.toFixed(1)}s
                </div>
                <div className="mono">
                  CityFlow emerg {compareResult.cityFlow.emergencyArrivalTicks}s · civ avg {compareResult.cityFlow.civilianAvgTicks.toFixed(1)}s
                </div>
              </div>
            )}
            {dispatchScene != null && (
              <p className="hint">Scene node #{dispatchScene}</p>
            )}
          </div>

          <div className="card grow">
            <h2>Cars</h2>
            <ul className="cars">
              {fleet.length === 0 && <li className="mute">No cars yet</li>}
              {fleet.map((v) => (
                <li key={v.id}>
                  <div className="car-top">
                    <span
                      className="car-swatch"
                      style={{
                        background: ["#4cc9f0", "#ffc857", "#2ee59d", "#ff9f6b", "#ff7a9a", "#80ffdb"][
                          v.id % 6
                        ],
                      }}
                    />
                    <strong>{v.name}{v.serviceClass && v.serviceClass !== "CIVILIAN" ? ` · ${v.serviceClass}` : ""}</strong>
                    <span className="mute mono">#{v.id}</span>
                    <span className={`pill ${v.arrived ? "ok" : ""}`}>
                      {v.arrived ? "done" : clockRunning ? "go" : "ready"}
                    </span>
                  </div>
                  <div className="car-route">
                    {nodeLabel(session, v.origin)} → {nodeLabel(session, v.destination)}
                  </div>
                  <div className="car-stats mono">
                    <span>short {v.plannedShortestTicks}s</span>
                    <span>live {v.plannedLiveTicks}s</span>
                    {v.actualTicks != null ? (
                      <span className="accent">total {v.actualTicks}s</span>
                    ) : (
                      <span>from 0s</span>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </aside>
      </div>
    </div>
  );
}
