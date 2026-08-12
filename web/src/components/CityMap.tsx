import type { EdgeDto, NodeDto, AccidentDto, VehicleDto, SessionMode } from "../types";

type Props = {
  mode: SessionMode;
  nodes: NodeDto[];
  edges: EdgeDto[];
  accidents: AccidentDto[];
  vehicles: VehicleDto[];
  selectedFrom: number | null;
  onSelectNode: (id: number) => void;
};

export function CityMap({
  mode,
  nodes,
  edges,
  accidents,
  vehicles,
  selectedFrom,
  onSelectNode,
}: Props) {
  const crashed = new Set(accidents.filter((a) => a.showCross).map((a) => a.edgeId));
  const byId = new Map(nodes.map((n) => [n.id, n]));

  const maxX = Math.max(1, ...nodes.map((n) => n.x));
  const maxY = Math.max(1, ...nodes.map((n) => n.y));
  const pad = 28;
  const width = maxX + pad * 2;
  const height = maxY + pad * 2;

  function sx(x: number) {
    return x + pad;
  }
  function sy(y: number) {
    return y + pad;
  }

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      className="city-map"
      role="img"
      aria-label="City map from Java session"
    >
      {edges.map((e) => {
        const a = byId.get(e.from);
        const b = byId.get(e.to);
        if (!a || !b) return null;
        const hit = crashed.has(e.id);
        return (
          <g key={e.id}>
            <line
              x1={sx(a.x)}
              y1={sy(a.y)}
              x2={sx(b.x)}
              y2={sy(b.y)}
              stroke={hit ? "#ff5a5f" : "#5a6572"}
              strokeWidth={hit ? 4 : 2.5}
              strokeLinecap="round"
            />
            {hit && (
              <text
                x={(sx(a.x) + sx(b.x)) / 2}
                y={(sy(a.y) + sy(b.y)) / 2}
                textAnchor="middle"
                dominantBaseline="middle"
                fill="#ff5a5f"
                fontSize="14"
                fontWeight="700"
              >
                ✕
              </text>
            )}
          </g>
        );
      })}

      {vehicles.map((v) => {
        if (v.positionType === "AT_NODE" && v.nodeId != null) {
          const n = byId.get(v.nodeId);
          if (!n) return null;
          return (
            <circle
              key={`car-${v.id}`}
              cx={sx(n.x)}
              cy={sy(n.y)}
              r={4}
              fill={v.arrived ? "#2ee59d" : "#4cc9f0"}
            />
          );
        }
        if (v.positionType === "ON_EDGE" && v.edgeId != null) {
          const edge = edges.find((e) => e.id === v.edgeId);
          if (!edge) return null;
          const a = byId.get(edge.from);
          const b = byId.get(edge.to);
          if (!a || !b) return null;
          const t = 0.45;
          return (
            <circle
              key={`car-${v.id}`}
              cx={sx(a.x) * (1 - t) + sx(b.x) * t}
              cy={sy(a.y) * (1 - t) + sy(b.y) * t}
              r={4}
              fill="#ffc857"
            />
          );
        }
        return null;
      })}

      {nodes.map((n) => (
        <g key={n.id} onClick={() => mode === "BUILD" && onSelectNode(n.id)} style={{ cursor: mode === "BUILD" ? "pointer" : "default" }}>
          <circle
            cx={sx(n.x)}
            cy={sy(n.y)}
            r={selectedFrom === n.id ? 9 : 7}
            fill="#1a1f24"
            stroke={selectedFrom === n.id ? "#4cc9f0" : "#9aa8b5"}
            strokeWidth="2"
          />
        </g>
      ))}

      <text x={12} y={16} fill="#9aa8b5" fontSize="11">
        {mode === "BUILD"
          ? selectedFrom == null
            ? "BUILD · click two nodes to draw a road"
            : `From node ${selectedFrom} · click destination`
          : "PLAY · live Java session"}
      </text>
    </svg>
  );
}
