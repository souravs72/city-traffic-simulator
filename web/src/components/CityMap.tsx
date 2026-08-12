import type { LightColor, MockAccident, SessionMode } from "../types";

type Props = {
  mode: SessionMode;
  accidents: MockAccident[];
  light: LightColor;
};

const NODES = [
  { id: 0, x: 40, y: 40, label: "R0C0" },
  { id: 1, x: 160, y: 40, label: "R0C1" },
  { id: 2, x: 280, y: 40, label: "R0C2" },
  { id: 3, x: 40, y: 160, label: "R1C0" },
  { id: 4, x: 160, y: 160, label: "R1C1" },
  { id: 5, x: 280, y: 160, label: "R1C2" },
  { id: 6, x: 40, y: 280, label: "R2C0" },
  { id: 7, x: 160, y: 280, label: "R2C1" },
  { id: 8, x: 280, y: 280, label: "R2C2" },
];

const EDGES = [
  { id: 0, from: 0, to: 1 },
  { id: 1, from: 1, to: 2 },
  { id: 2, from: 0, to: 3 },
  { id: 3, from: 1, to: 4 },
  { id: 4, from: 2, to: 5 },
  { id: 5, from: 3, to: 4 },
  { id: 6, from: 4, to: 5 },
  { id: 7, from: 3, to: 6 },
  { id: 8, from: 4, to: 7 },
  { id: 9, from: 5, to: 8 },
  { id: 10, from: 6, to: 7 },
  { id: 11, from: 7, to: 8 },
];

function node(id: number) {
  return NODES.find((n) => n.id === id)!;
}

export function CityMap({ mode, accidents, light }: Props) {
  const crashed = new Set(accidents.map((a) => a.edgeId));
  const stroke =
    light === "GREEN" ? "#2ee59d" : light === "YELLOW" ? "#ffc857" : "#ff5a5f";

  return (
    <svg viewBox="0 0 320 320" className="city-map" role="img" aria-label="Preview city grid">
      {EDGES.map((e) => {
        const a = node(e.from);
        const b = node(e.to);
        const hit = crashed.has(e.id);
        return (
          <g key={e.id}>
            <line
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke={hit ? "#ff5a5f" : "#5a6572"}
              strokeWidth={hit ? 5 : 3}
              strokeLinecap="round"
            />
            {hit && (
              <text
                x={(a.x + b.x) / 2}
                y={(a.y + b.y) / 2}
                textAnchor="middle"
                dominantBaseline="middle"
                fill="#ff5a5f"
                fontSize="18"
                fontWeight="700"
              >
                ✕
              </text>
            )}
          </g>
        );
      })}
      {NODES.map((n) => (
        <g key={n.id}>
          <circle cx={n.x} cy={n.y} r={10} fill="#1a1f24" stroke={stroke} strokeWidth="2.5" />
          <text x={n.x} y={n.y + 22} textAnchor="middle" fill="#9aa8b5" fontSize="8">
            {n.label}
          </text>
        </g>
      ))}
      <text x="16" y="18" fill="#9aa8b5" fontSize="10">
        {mode === "BUILD" ? "BUILD · click/drag coming soon" : "PLAY · live overlay preview"}
      </text>
    </svg>
  );
}
