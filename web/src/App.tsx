import { useCallback, useEffect, useState } from "react";
import "./App.css";
import { CityMap } from "./components/CityMap";
import type { SessionSnapshot } from "./types";
import * as api from "./api";

export default function App() {
  const [session, setSession] = useState<SessionSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedFrom, setSelectedFrom] = useState<number | null>(null);

  const apply = useCallback((s: SessionSnapshot) => {
    setSession(s);
    setError(null);
  }, []);

  async function boot() {
    setBusy(true);
    try {
      apply(await api.createSession({ rows: 8, cols: 8, fleetSize: 10, seed: 42 }));
      setSelectedFrom(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to create session");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void boot();
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

  async function onSelectNode(id: number) {
    if (!session || session.mode !== "BUILD") return;
    if (selectedFrom == null) {
      setSelectedFrom(id);
      return;
    }
    if (selectedFrom === id) {
      setSelectedFrom(null);
      return;
    }
    const from = selectedFrom;
    setSelectedFrom(null);
    await run(() => api.connectEdge({ from, to: id, capacity: 3, twoWay: false }));
  }

  if (!session) {
    return (
      <div className="shell">
        <h1 className="brand">City Traffic Simulator</h1>
        <p className="mute">{error ?? "Starting Java session…"}</p>
        <button type="button" className="primary" onClick={() => void boot()} disabled={busy}>
          Retry connect
        </button>
        <p className="hint">Start API: mvn -q exec:java -Dexec.mainClass=com.traffic.api.ApiMain</p>
      </div>
    );
  }

  return (
    <div className="shell">
      <header className="hero-bar">
        <div>
          <p className="eyebrow">React UI · Java engine</p>
          <h1 className="brand">City Traffic Simulator</h1>
        </div>
        <div className={`mode-pill mode-${session.mode.toLowerCase()}`}>{session.mode}</div>
      </header>

      {error && <p className="error-banner">{error}</p>}

      <main className="layout">
        <section className="map-stage" aria-label="City map">
          <CityMap
            mode={session.mode}
            nodes={session.nodes}
            edges={session.edges}
            accidents={session.accidents}
            vehicles={session.vehicles}
            selectedFrom={selectedFrom}
            onSelectNode={(id) => void onSelectNode(id)}
          />
          <div className="map-caption">
            {session.nodeCount} nodes · {session.edgeCount} edges
            {session.hasUnappliedEdits ? " · unapplied edits" : ""}
          </div>
        </section>

        <aside className="side">
          <div className="panel">
            <h2>Session</h2>
            <div className="btn-row">
              <button
                type="button"
                className={session.mode === "BUILD" ? "active" : ""}
                disabled={busy}
                onClick={() => void run(api.buildMode)}
              >
                Build
              </button>
              <button
                type="button"
                className={session.mode === "PLAY" ? "active" : ""}
                disabled={busy}
                onClick={() => void run(api.playMode)}
              >
                Play
              </button>
            </div>
            <button
              type="button"
              className="primary"
              disabled={busy || session.mode !== "PLAY"}
              onClick={() => void run(api.stepTick)}
            >
              Step tick
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy || session.mode !== "PLAY"}
              onClick={() => void run(() => api.runTicks(10))}
            >
              Run +10
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy || !session.hasUnappliedEdits}
              onClick={() => void run(api.applyEdits)}
            >
              Apply map edits
            </button>
            <button type="button" className="ghost" disabled={busy} onClick={() => void boot()}>
              New city
            </button>
          </div>

          <div className="panel">
            <h2>World</h2>
            <dl className="stats">
              <div>
                <dt>Tick</dt>
                <dd className="mono">{session.worldTick}</dd>
              </div>
              <div>
                <dt>Arrived</dt>
                <dd className="mono">
                  {session.arrivedCount}/{session.fleetSize}
                </dd>
              </div>
              <div>
                <dt>✕</dt>
                <dd className="mono">{session.accidents.length}</dd>
              </div>
            </dl>
            <button
              type="button"
              className="ghost"
              disabled={busy || session.edges.length === 0}
              onClick={() => {
                const edge = session.edges[Math.floor(Math.random() * session.edges.length)];
                void run(() => api.spawnAccident({ edgeId: edge.id, durationTicks: 12 }));
              }}
            >
              Spawn random ✕ accident
            </button>
            <ul className="accident-list">
              {session.accidents.length === 0 && <li className="mute">Roads clear</li>}
              {session.accidents.map((a) => (
                <li key={a.id}>
                  <span className="cross" aria-hidden>
                    ✕
                  </span>
                  <span>
                    edge {a.edgeId}: {a.caption}{" "}
                    <span className="mono">({a.ticksRemaining})</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </aside>
      </main>
    </div>
  );
}
