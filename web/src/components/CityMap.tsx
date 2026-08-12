import { useEffect, useRef, useState } from "react";
import type { EdgeDto, NodeDto, AccidentDto, VehicleDto, ClickTool, RoadType } from "../types";

export type MapTool = ClickTool | "NODE";

type Props = {
  tool: MapTool;
  nodes: NodeDto[];
  edges: EdgeDto[];
  accidents: AccidentDto[];
  vehicles: VehicleDto[];
  selectedFrom: number | null;
  tripFrom: number | null;
  selectedNodeId: number | null;
  selectedEdgeId: number | null;
  showSignals?: boolean;
  animateTravel?: boolean;
  animStartedAt?: number;
  tickDurationMs?: number;
  onSelectNode: (id: number) => void;
  onSelectEdge: (id: number) => void;
  onAddNode: (x: number, y: number) => void;
};

const VIEW_W = 1400;
const VIEW_H = 860;

type Cam = { x: number; y: number; w: number; h: number };

const CAR_COLORS = ["#4cc9f0", "#ffc857", "#2ee59d", "#ff9f6b", "#ff7a9a", "#80ffdb", "#b8f2e6"];

function facilityStroke(facility?: string | null): string | null {
  switch (facility) {
    case "HOSPITAL":
      return "#ff6b6b";
    case "POLICE_STATION":
      return "#4cc9f0";
    case "FIRE_STATION":
      return "#ff9f1c";
    case "VIP_SITE":
      return "#c77dff";
    default:
      return null;
  }
}

function serviceCarColor(serviceClass: string | null | undefined, id: number): string {
  switch (serviceClass) {
    case "FIRE":
      return "#ff9f1c";
    case "AMBULANCE":
      return "#ff6b6b";
    case "POLICE":
      return "#4cc9f0";
    case "VIP":
      return "#c77dff";
    default:
      return carColor(id);
  }
}

function carColor(id: number): string {
  return CAR_COLORS[id % CAR_COLORS.length];
}

function signalFill(color: string | null): string | null {
  if (color === "GREEN") return "#2ee59d";
  if (color === "YELLOW") return "#ffc857";
  if (color === "RED") return "#ff7a7e";
  return null;
}

function approachPoint(a: NodeDto, b: NodeDto): { x: number; y: number } {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len = Math.hypot(dx, dy) || 1;
  const dist = Math.min(24, Math.max(14, len * 0.22));
  return { x: a.x + (dx / len) * dist, y: a.y + (dy / len) * dist };
}

function roadWidth(type: string | undefined, busy: boolean, selected: boolean): number {
  const base =
    type === "HIGHWAY" ? 5.5 : type === "ALLEY" ? 2 : 3.2;
  if (selected) return base + 1.5;
  if (busy) return base + 1;
  return base;
}

/** Neutral road + congestion heat (Google-Maps style load). */
function roadStroke(
  e: EdgeDto,
  hit: boolean,
  selected: boolean,
  busy: boolean,
): string {
  if (hit) return "#ff5a5f";
  if (selected) return "#4cc9f0";
  if (busy) return "#7ad7f0";
  const cap = Math.max(1, e.capacity);
  const load = Math.min(1, (e.occupancy ?? 0) / cap);
  if (load <= 0.05) {
    return e.roadType === "HIGHWAY" ? "#6a7684" : e.roadType === "ALLEY" ? "#4a5560" : "#5a6572";
  }
  if (load < 0.45) return "#ffc857";
  if (load < 0.75) return "#ff9f6b";
  return "#ff6b6f";
}

function useTickFraction(active: boolean, startedAt: number, durationMs: number): number {
  const [frac, setFrac] = useState(0);
  useEffect(() => {
    if (!active) {
      setFrac(0);
      return;
    }
    let raf = 0;
    const loop = (now: number) => {
      const t = Math.min(1, Math.max(0, (now - startedAt) / durationMs));
      setFrac(t);
      if (t < 1) raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [active, startedAt, durationMs]);
  return active ? frac : 0;
}

function carPosition(
  v: VehicleDto,
  nodes: Map<number, NodeDto>,
  edges: EdgeDto[],
  tickFrac: number,
): { x: number; y: number; angle: number } | null {
  if (v.positionType === "AT_NODE" && v.nodeId != null) {
    const n = nodes.get(v.nodeId);
    if (!n) return null;
    return { x: n.x, y: n.y, angle: 0 };
  }
  if (v.positionType === "ON_EDGE" && v.edgeId != null) {
    const edge = edges.find((e) => e.id === v.edgeId);
    if (!edge) return null;
    const a = nodes.get(edge.from);
    const b = nodes.get(edge.to);
    if (!a || !b) return null;
    const total = Math.max(1, edge.baseWeight);
    const left = Math.max(1, v.ticksRemaining ?? total);
    const start = (total - left) / total;
    const end = Math.min(1, (total - left + 1) / total);
    const t = start + (end - start) * tickFrac;
    return {
      x: a.x * (1 - t) + b.x * t,
      y: a.y * (1 - t) + b.y * t,
      angle: (Math.atan2(b.y - a.y, b.x - a.x) * 180) / Math.PI,
    };
  }
  return null;
}

function CarSprite({ v, x, y, angle }: { v: VehicleDto; x: number; y: number; angle: number }) {
  const color = v.arrived ? "#2ee59d" : serviceCarColor(v.serviceClass, v.id);
  const label = v.name || `Car ${v.id}`;
  return (
    <g data-car={v.id} className={`car-sprite ${v.arrived ? "arrived" : "driving"}`} pointerEvents="none">
      <g transform={`translate(${x}, ${y - 28})`}>
        <rect
          x={-Math.min(54, 8 + label.length * 3.6)}
          y={-11}
          rx={10}
          ry={10}
          width={Math.min(108, 16 + label.length * 7.2)}
          height={20}
          fill="#0f1419"
          stroke={color}
          strokeWidth={1.6}
          opacity={0.95}
        />
        <polygon points="-4,9 4,9 0,15" fill={color} />
        <text x={0} y={3} textAnchor="middle" fill={color} fontSize="10" fontWeight="800" fontFamily="Outfit, sans-serif">
          {label}
        </text>
      </g>
      <g transform={`translate(${x}, ${y}) rotate(${angle})`}>
        <ellipse cx={0} cy={7} rx={12} ry={3.5} fill="rgba(0,0,0,0.25)" />
        <rect x={-14} y={-7} width={28} height={12} rx={4} fill={color} stroke="#0b1014" strokeWidth={1.4} />
        <rect x={-7} y={-10} width={14} height={7} rx={2.5} fill="#f4fbff" opacity={0.9} />
        <circle cx={-8} cy={6} r={3.2} fill="#1a1f24" />
        <circle cx={8} cy={6} r={3.2} fill="#1a1f24" />
        {v.arrived && (
          <text x={0} y={-14} textAnchor="middle" fontSize="12">
            🏁
          </text>
        )}
      </g>
    </g>
  );
}

export function CityMap({
  tool,
  nodes,
  edges,
  accidents,
  vehicles,
  selectedFrom,
  tripFrom,
  selectedNodeId,
  selectedEdgeId,
  showSignals = false,
  animateTravel = false,
  animStartedAt = 0,
  tickDurationMs = 1000,
  onSelectNode,
  onSelectEdge,
  onAddNode,
}: Props) {
  const tickFrac = useTickFraction(animateTravel, animStartedAt, tickDurationMs);
  const [cam, setCam] = useState<Cam>({ x: 0, y: 0, w: VIEW_W, h: VIEW_H });
  const drag = useRef<{ px: number; py: number; cx: number; cy: number } | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);

  const crashed = new Set(accidents.filter((a) => a.showCross).map((a) => a.edgeId));
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const busyEdges = new Set(
    vehicles.filter((v) => v.positionType === "ON_EDGE" && v.edgeId != null).map((v) => v.edgeId!),
  );

  function clientToWorld(e: { clientX: number; clientY: number }) {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0 };
    const pt = svg.createSVGPoint();
    pt.x = e.clientX;
    pt.y = e.clientY;
    const ctm = svg.getScreenCTM();
    if (!ctm) return { x: 0, y: 0 };
    const local = pt.matrixTransform(ctm.inverse());
    return { x: local.x, y: local.y };
  }

  function onWheel(e: React.WheelEvent<SVGSVGElement>) {
    e.preventDefault();
    const world = clientToWorld(e);
    const factor = e.deltaY > 0 ? 1.12 : 1 / 1.12;
    setCam((prev) => {
      const nw = Math.min(VIEW_W * 1.4, Math.max(VIEW_W * 0.28, prev.w * factor));
      const nh = (nw / prev.w) * prev.h;
      const rx = (world.x - prev.x) / prev.w;
      const ry = (world.y - prev.y) / prev.h;
      return {
        w: nw,
        h: nh,
        x: world.x - rx * nw,
        y: world.y - ry * nh,
      };
    });
  }

  return (
    <div className="map-shell">
      <svg
        ref={svgRef}
        viewBox={`${cam.x} ${cam.y} ${cam.w} ${cam.h}`}
        className="city-map"
        role="img"
        aria-label="City map"
        preserveAspectRatio="xMidYMid meet"
        onWheel={onWheel}
        onPointerDown={(e) => {
          if (e.button === 1 || e.button === 2 || e.altKey) {
            e.preventDefault();
            drag.current = { px: e.clientX, py: e.clientY, cx: cam.x, cy: cam.y };
            (e.target as Element).setPointerCapture?.(e.pointerId);
          }
        }}
        onPointerMove={(e) => {
          if (!drag.current || !svgRef.current) return;
          const rect = svgRef.current.getBoundingClientRect();
          const dx = ((e.clientX - drag.current.px) / rect.width) * cam.w;
          const dy = ((e.clientY - drag.current.py) / rect.height) * cam.h;
          setCam((c) => ({ ...c, x: drag.current!.cx - dx, y: drag.current!.cy - dy }));
        }}
        onPointerUp={() => {
          drag.current = null;
        }}
        onContextMenu={(e) => e.preventDefault()}
        onClick={(e) => {
          if (tool !== "NODE") return;
          if ((e.target as Element).closest("[data-node],[data-edge],[data-car]")) return;
          const { x, y } = clientToWorld(e);
          onAddNode(Math.round(x), Math.round(y));
        }}
        style={{ cursor: tool === "NODE" ? "crosshair" : "grab" }}
      >
        <rect x={cam.x - 40} y={cam.y - 40} width={cam.w + 80} height={cam.h + 80} fill="transparent" />

        {edges.map((e) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null;
          const hit = crashed.has(e.id);
          const selected = selectedEdgeId === e.id;
          const busy = busyEdges.has(e.id);
          const load = (e.occupancy ?? 0) / Math.max(1, e.capacity);
          return (
            <g key={e.id} data-edge={e.id}>
              <line
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke="transparent"
                strokeWidth={18}
                strokeLinecap="round"
                style={{ cursor: tool === "CRASH" ? "cell" : "pointer" }}
                onClick={(ev) => {
                  ev.stopPropagation();
                  onSelectEdge(e.id);
                }}
              />
              {load > 0.2 && (
                <line
                  x1={a.x}
                  y1={a.y}
                  x2={b.x}
                  y2={b.y}
                  stroke="rgba(255, 107, 111, 0.18)"
                  strokeWidth={roadWidth(e.roadType, busy, selected) + 6}
                  strokeLinecap="round"
                  pointerEvents="none"
                />
              )}
              <line
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke={roadStroke(e, hit, selected, busy)}
                strokeWidth={roadWidth(e.roadType, busy, selected)}
                strokeLinecap="round"
                pointerEvents="none"
              />
              {hit && (
                <text
                  x={(a.x + b.x) / 2}
                  y={(a.y + b.y) / 2}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  fill="#ff5a5f"
                  fontSize="14"
                  fontWeight="700"
                  pointerEvents="none"
                >
                  ✕
                </text>
              )}
            </g>
          );
        })}

        {showSignals &&
          edges.map((e) => {
            if (!e.lightColor) return null;
            const a = byId.get(e.from);
            const b = byId.get(e.to);
            if (!a || !b) return null;
            const fill = signalFill(e.lightColor);
            if (!fill) return null;
            const p = approachPoint(a, b);
            return (
              <g key={`light-${e.id}`} pointerEvents="none">
                <circle cx={p.x} cy={p.y} r={5.5} fill="#0b1014" opacity={0.85} />
                <circle cx={p.x} cy={p.y} r={3.6} fill={fill} />
              </g>
            );
          })}

        {vehicles.map((v) => {
          const o = byId.get(v.origin);
          const d = byId.get(v.destination);
          if (!o || !d || v.arrived) return null;
          const color = carColor(v.id);
          return (
            <g key={`od-${v.id}`} pointerEvents="none" opacity={0.18}>
              <line x1={o.x} y1={o.y} x2={d.x} y2={d.y} stroke={color} strokeWidth={1.5} strokeDasharray="5 6" />
            </g>
          );
        })}

        {nodes.map((n) => {
          const isTripStart = tripFrom === n.id;
          const isRoadStart = selectedFrom === n.id;
          const isSelected = selectedNodeId === n.id;
          const active = isTripStart || isRoadStart || isSelected;
          return (
            <g
              key={n.id}
              data-node={n.id}
              onClick={(ev) => {
                ev.stopPropagation();
                onSelectNode(n.id);
              }}
              style={{ cursor: "pointer" }}
            >
              <circle
                cx={n.x}
                cy={n.y}
                r={active ? 15 : 11}
                fill={isTripStart ? "#0f2e26" : isSelected ? "#1c2833" : "#151a1f"}
                stroke={
                  isSelected
                    ? "#ff7a7e"
                    : isTripStart
                      ? "#2ee59d"
                      : isRoadStart
                        ? "#4cc9f0"
                        : facilityStroke(n.facility) ?? "#7d8b99"
                }
                strokeWidth={isSelected || facilityStroke(n.facility) ? 2.5 : 2}
              />
              <text x={n.x} y={n.y + 3} textAnchor="middle" fill="#d7e0e8" fontSize="9" fontWeight="600" pointerEvents="none">
                {n.label}
              </text>
            </g>
          );
        })}

        {vehicles.map((v) => {
          const pos = carPosition(v, byId, edges, tickFrac);
          if (!pos) return null;
          return <CarSprite key={`car-${v.id}`} v={v} x={pos.x} y={pos.y} angle={pos.angle} />;
        })}

        {!animateTravel && (
          <text x={cam.x + 16} y={cam.y + 22} fill="#8b98a6" fontSize="12">
            {tool === "NODE"
              ? "Click to add · scroll zoom · Alt-drag pan"
              : tool === "ROAD"
                ? selectedFrom == null
                  ? "Click two nodes to draw"
                  : `From ${selectedFrom} · click end`
                : tool === "CRASH"
                  ? "Click a road to close it (live detour)"
                  : tripFrom == null
                    ? "Name the car, then START → END"
                    : `Start ${tripFrom} · click END`}
          </text>
        )}
      </svg>

      <svg className="minimap" viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} aria-label="Minimap">
        <rect x={0} y={0} width={VIEW_W} height={VIEW_H} fill="#0f1419" />
        {edges.map((e) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null;
          return (
            <line
              key={e.id}
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke="#3d4650"
              strokeWidth={e.roadType === "HIGHWAY" ? 3 : 1.5}
            />
          );
        })}
        {nodes.map((n) => (
          <circle key={n.id} cx={n.x} cy={n.y} r={3} fill="#8b98a6" />
        ))}
        <rect
          x={cam.x}
          y={cam.y}
          width={cam.w}
          height={cam.h}
          fill="rgba(76,201,240,0.12)"
          stroke="#4cc9f0"
          strokeWidth={8}
        />
      </svg>
    </div>
  );
}

export type { RoadType };
