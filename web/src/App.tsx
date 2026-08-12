import { useMemo, useState } from "react";
import "./App.css";
import { CityMap } from "./components/CityMap";
import type { SessionMode, LightColor, MockAccident } from "./types";

const INITIAL_ACCIDENTS: MockAccident[] = [
  { edgeId: 7, caption: "Duck crossing committee", ticksRemaining: 6 },
];

export default function App() {
  const [mode, setMode] = useState<SessionMode>("BUILD");
  const [tick, setTick] = useState(0);
  const [light, setLight] = useState<LightColor>("GREEN");
  const [accidents, setAccidents] = useState(INITIAL_ACCIDENTS);
  const [arrived, setArrived] = useState(0);
  const fleetSize = 12;

  const lightClass = useMemo(() => light.toLowerCase(), [light]);

  function enterPlay() {
    setMode("PLAY");
  }

  function enterBuild() {
    setMode("BUILD");
  }

  function stepOnce() {
    if (mode !== "PLAY") return;
    setTick((t) => t + 1);
    setLight((prev) => {
      if (prev === "GREEN") return "YELLOW";
      if (prev === "YELLOW") return "RED";
      return "GREEN";
    });
    setAccidents((list) =>
      list
        .map((a) => ({ ...a, ticksRemaining: a.ticksRemaining - 1 }))
        .filter((a) => a.ticksRemaining > 0),
    );
    setArrived((n) => Math.min(fleetSize, n + (tick % 3 === 2 ? 1 : 0)));
  }

  return (
    <div className="shell">
      <header className="hero-bar">
        <div>
          <p className="eyebrow">Concurrent graph playground</p>
          <h1 className="brand">City Traffic Simulator</h1>
        </div>
        <div className={`mode-pill mode-${mode.toLowerCase()}`}>{mode}</div>
      </header>

      <main className="layout">
        <section className="map-stage" aria-label="City map">
          <CityMap mode={mode} accidents={accidents} light={light} />
          <div className="map-caption">
            Drag roads in BUILD · ✕ marks accidents · lights cycle in PLAY
            <span className="hint"> (preview UI — Java engine API next)</span>
          </div>
        </section>

        <aside className="side">
          <div className="panel">
            <h2>Session</h2>
            <div className="btn-row">
              <button
                type="button"
                className={mode === "BUILD" ? "active" : ""}
                onClick={enterBuild}
              >
                Build
              </button>
              <button
                type="button"
                className={mode === "PLAY" ? "active" : ""}
                onClick={enterPlay}
              >
                Play
              </button>
            </div>
            <button
              type="button"
              className="primary"
              disabled={mode !== "PLAY"}
              onClick={stepOnce}
            >
              Step tick
            </button>
          </div>

          <div className="panel">
            <h2>Signals</h2>
            <div className="signal-row" aria-live="polite">
              <span className={`lamp green ${lightClass === "green" ? "on" : ""}`} />
              <span className={`lamp yellow ${lightClass === "yellow" ? "on" : ""}`} />
              <span className={`lamp red ${lightClass === "red" ? "on" : ""}`} />
              <span className="mono">{light}</span>
            </div>
          </div>

          <div className="panel">
            <h2>World</h2>
            <dl className="stats">
              <div>
                <dt>Tick</dt>
                <dd className="mono">{tick}</dd>
              </div>
              <div>
                <dt>Arrived</dt>
                <dd className="mono">
                  {arrived}/{fleetSize}
                </dd>
              </div>
              <div>
                <dt>Accidents</dt>
                <dd className="mono">{accidents.length}</dd>
              </div>
            </dl>
            <ul className="accident-list">
              {accidents.length === 0 && <li className="mute">Roads clear</li>}
              {accidents.map((a) => (
                <li key={a.edgeId}>
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
