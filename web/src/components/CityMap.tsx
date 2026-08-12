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

function roadWidth(
  type: string | undefined,
  busy: boolean,
  selected: boolean,
  far = false,
): number {
  const base =
    type === "HIGHWAY" ? (far ? 3.2 : 5.5) : type === "ALLEY" ? (far ? 1.1 : 2) : far ? 1.8 : 3.2;
  if (selected) return base + 1.5;
  if (busy) return base + (far ? 0.4 : 1);
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
      const raw = Math.min(1, Math.max(0, (now - startedAt) / durationMs));
      // Ease-in-out keeps cars sliding smoothly between 1s ticks.
      const eased = raw < 0.5 ? 2 * raw * raw : 1 - Math.pow(-2 * raw + 2, 2) / 2;
      setFrac(eased);
      if (raw < 1) raf = requestAnimationFrame(loop);
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

function isPriority(serviceClass: string | null | undefined): boolean {
  return !!serviceClass && serviceClass !== "CIVILIAN";
}

function inView(x: number, y: number, cam: Cam, pad = 48): boolean {
  return x >= cam.x - pad && x <= cam.x + cam.w + pad && y >= cam.y - pad && y <= cam.y + cam.h + pad;
}

type Bounds = { minX: number; minY: number; maxX: number; maxY: number };

function contentBounds(nodes: NodeDto[]): Bounds {
  if (nodes.length === 0) {
    return { minX: 0, minY: 0, maxX: VIEW_W, maxY: VIEW_H };
  }
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    minX = Math.min(minX, n.x);
    minY = Math.min(minY, n.y);
    maxX = Math.max(maxX, n.x);
    maxY = Math.max(maxY, n.y);
  }
  return { minX, minY, maxX, maxY };
}

const ZOOM_IN_MAX = VIEW_W * 0.22; // smaller viewBox = closer
const ZOOM_OUT_MAX_FACTOR = 2.35;

function zoomLimits(b: Bounds): { minW: number; maxW: number } {
  const pad = 120;
  const worldW = Math.max(b.maxX - b.minX + pad * 2, 1);
  const maxW = Math.min(VIEW_W * ZOOM_OUT_MAX_FACTOR, worldW * 2.5);
  return { minW: ZOOM_IN_MAX, maxW: Math.max(maxW, ZOOM_IN_MAX * 1.01) };
}

/** Keep the viewport overlapping the city so drag can't lose the map. */
function clampCam(cam: Cam, b: Bounds): Cam {
  const pad = 120;
  const minX = b.minX - pad;
  const minY = b.minY - pad;
  const maxX = b.maxX + pad;
  const maxY = b.maxY + pad;
  const { minW, maxW } = zoomLimits(b);
  const aspect = VIEW_H / VIEW_W;
  let w = Math.min(Math.max(cam.w, minW), maxW);
  let h = w * aspect;
  // Preserve requested aspect from cam when sane.
  if (cam.w > 0 && cam.h > 0) {
    h = w * (cam.h / cam.w);
  }
  let x = cam.x;
  let y = cam.y;
  x = Math.min(x, maxX - w * 0.2);
  y = Math.min(y, maxY - h * 0.2);
  x = Math.max(x, minX - w * 0.8);
  y = Math.max(y, minY - h * 0.8);
  if (![x, y, w, h].every(Number.isFinite)) {
    return { x: minX - 40, y: minY - 40, w: VIEW_W, h: VIEW_H };
  }
  return { x, y, w, h };
}

function zoomAt(cam: Cam, worldX: number, worldY: number, factor: number, b: Bounds): Cam {
  const { minW, maxW } = zoomLimits(b);
  const targetW = Math.min(Math.max(cam.w * factor, minW), maxW);
  const scale = targetW / cam.w;
  const targetH = cam.h * scale;
  const rx = (worldX - cam.x) / Math.max(cam.w, 1);
  const ry = (worldY - cam.y) / Math.max(cam.h, 1);
  return clampCam(
    {
      w: targetW,
      h: targetH,
      x: worldX - rx * targetW,
      y: worldY - ry * targetH,
    },
    b,
  );
}

function fitCamToBounds(b: Bounds): Cam {
  const pad = 70;
  const bw = Math.max(b.maxX - b.minX, 80) + pad * 2;
  const bh = Math.max(b.maxY - b.minY, 80) + pad * 2;
  const aspect = VIEW_W / VIEW_H;
  let w = bw;
  let h = bh;
  if (w / h > aspect) h = w / aspect;
  else w = h * aspect;
  const cx = (b.minX + b.maxX) / 2;
  const cy = (b.minY + b.maxY) / 2;
  return clampCam({ x: cx - w / 2, y: cy - h / 2, w, h }, b);
}

function applyViewBox(svg: SVGSVGElement | null, cam: Cam) {
  if (!svg) return;
  svg.setAttribute("viewBox", `${cam.x} ${cam.y} ${cam.w} ${cam.h}`);
}

type Lod = {
  zoom: number;
  dense: boolean;
  far: boolean;
  near: boolean;
  showNodeLabels: boolean;
  showOdLines: boolean;
  showCivilianRoutes: boolean;
  showEdgeTags: boolean;
  civilianDots: boolean;
};

function computeLod(cam: Cam, nodeCount: number, activeCars: number): Lod {
  const zoom = VIEW_W / cam.w;
  const dense = nodeCount > 70 || activeCars > 60;
  const far = zoom < 1.05 || dense;
  const near = zoom >= 1.55;
  return {
    zoom,
    dense,
    far,
    near,
    showNodeLabels: !dense || near,
    showOdLines: activeCars <= 28 && !dense,
    showCivilianRoutes: activeCars <= 50,
    showEdgeTags: near || (!dense && zoom >= 1.15),
    civilianDots: dense && !near,
  };
}

function CarSprite({
  v,
  x,
  y,
  angle,
  compact,
  showLabel,
}: {
  v: VehicleDto;
  x: number;
  y: number;
  angle: number;
  compact?: boolean;
  showLabel?: boolean;
}) {
  const color = v.arrived ? "#2ee59d" : serviceCarColor(v.serviceClass, v.id);
  const label = v.name || `Car ${v.id}`;
  const priority = isPriority(v.serviceClass);

  if (compact && !priority) {
    // Directed capsule — readable as traffic, not a junction/light dot.
    return (
      <g data-car={v.id} pointerEvents="none" opacity={0.9}>
        <g transform={`translate(${x}, ${y}) rotate(${angle})`}>
          <rect x={-5.5} y={-2.2} width={11} height={4.4} rx={2.2} fill={color} stroke="#0b1014" strokeWidth={0.7} />
          <polygon points="5.2,-2.4 9.2,0 5.2,2.4" fill={color} stroke="#0b1014" strokeWidth={0.5} />
        </g>
      </g>
    );
  }

  const labelOn = showLabel ?? priority;
  return (
    <g
      data-car={v.id}
      className={`car-sprite ${v.arrived ? "arrived" : priority ? "driving" : "driving quiet"}`}
      pointerEvents="none"
    >
      {labelOn && (
        <g transform={`translate(${x}, ${y - (priority ? 26 : 22)})`}>
          <rect
            x={-Math.min(50, 6 + label.length * 3.2)}
            y={-9}
            rx={8}
            ry={8}
            width={Math.min(100, 12 + label.length * 6.4)}
            height={16}
            fill="#0f1419"
            stroke={color}
            strokeWidth={1.4}
            opacity={0.92}
          />
          <polygon points="-3,7 3,7 0,12" fill={color} />
          <text
            x={0}
            y={2.5}
            textAnchor="middle"
            fill={color}
            fontSize={priority ? "9" : "8"}
            fontWeight="800"
            fontFamily="Outfit, sans-serif"
          >
            {label}
          </text>
        </g>
      )}
      <g transform={`translate(${x}, ${y}) rotate(${angle})`}>
        <ellipse cx={0} cy={6} rx={priority ? 11 : 8} ry={3} fill="rgba(0,0,0,0.22)" />
        <rect
          x={priority ? -13 : -9}
          y={priority ? -6 : -4}
          width={priority ? 26 : 18}
          height={priority ? 11 : 8}
          rx={3.5}
          fill={color}
          stroke="#0b1014"
          strokeWidth={1.2}
        />
        {priority && <rect x={-6} y={-9} width={12} height={6} rx={2} fill="#f4fbff" opacity={0.9} />}
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
  const bounds = contentBounds(nodes);
  const [cam, setCam] = useState<Cam>(() => ({ x: 0, y: 0, w: VIEW_W, h: VIEW_H }));
  const [panning, setPanning] = useState(false);
  const drag = useRef<{
    px: number;
    py: number;
    cx: number;
    cy: number;
    cw: number;
    ch: number;
    moved: boolean;
    live: Cam | null;
  } | null>(null);
  const didPan = useRef(false);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const panRaf = useRef(0);
  const camRef = useRef(cam);
  camRef.current = cam;

  // Refit when the city topology is replaced (node count jumps).
  const nodeCountRef = useRef(nodes.length);
  useEffect(() => {
    if (nodeCountRef.current === nodes.length) {
      setCam((prev) => clampCam(prev, bounds));
      return;
    }
    nodeCountRef.current = nodes.length;
    setCam(fitCamToBounds(bounds));
  }, [nodes.length, bounds.minX, bounds.minY, bounds.maxX, bounds.maxY]);

  const crashed = new Set(accidents.filter((a) => a.showCross).map((a) => a.edgeId));
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const busyEdges = new Set(
    vehicles.filter((v) => v.positionType === "ON_EDGE" && v.edgeId != null).map((v) => v.edgeId!),
  );

  const priorityRouteEdges = new Set<number>();
  const civilianDetourEdges = new Set<number>();
  for (const v of vehicles) {
    if (v.arrived || !v.remainingEdgeIds?.length) continue;
    const target = v.serviceClass && v.serviceClass !== "CIVILIAN" ? priorityRouteEdges : civilianDetourEdges;
    for (const id of v.remainingEdgeIds) target.add(id);
  }
  // Prefer priority glow when both share an edge.
  for (const id of priorityRouteEdges) civilianDetourEdges.delete(id);

  const activeCars = vehicles.reduce((n, v) => n + (v.arrived ? 0 : 1), 0);
  const lod = computeLod(cam, nodes.length, activeCars);
  // Before Start race, a huge parked fleet stacks on origins — show counts, not 1k sprites.
  const parkBuild = !animateTravel && activeCars > 70;
  const parkedByNode = new Map<number, number>();
  if (parkBuild) {
    for (const v of vehicles) {
      if (v.arrived) continue;
      if (v.positionType === "AT_NODE" && v.nodeId != null) {
        parkedByNode.set(v.nodeId, (parkedByNode.get(v.nodeId) ?? 0) + 1);
      } else if (v.positionType !== "ON_EDGE") {
        parkedByNode.set(v.origin, (parkedByNode.get(v.origin) ?? 0) + 1);
      }
    }
  }
  // Dense fleets: only glow priority corridors/routes — civilian path paint is noise.
  if (!lod.showCivilianRoutes) {
    civilianDetourEdges.clear();
  }

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

  function commitCam(next: Cam) {
    const clamped = clampCam(next, bounds);
    camRef.current = clamped;
    applyViewBox(svgRef.current, clamped);
    setCam(clamped);
  }

  function onWheel(e: React.WheelEvent<SVGSVGElement>) {
    e.preventDefault();
    const world = clientToWorld(e);
    // Smooth, cursor-anchored zoom (trackpads + mouse wheels).
    const intensity = Math.min(0.22, Math.abs(e.deltaY) / 500);
    const factor = e.deltaY > 0 ? 1 + intensity : 1 / (1 + intensity);
    commitCam(zoomAt(camRef.current, world.x, world.y, factor, bounds));
  }

  function zoomButton(direction: "in" | "out") {
    const prev = camRef.current;
    const cx = prev.x + prev.w / 2;
    const cy = prev.y + prev.h / 2;
    const factor = direction === "in" ? 1 / 1.28 : 1.28;
    commitCam(zoomAt(prev, cx, cy, factor, bounds));
  }

  function fitCity() {
    commitCam(fitCamToBounds(bounds));
  }

  const { minW, maxW } = zoomLimits(bounds);
  const canZoomIn = cam.w > minW * 1.02;
  const canZoomOut = cam.w < maxW * 0.98;

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if (e.key === "+" || e.key === "=") {
        e.preventDefault();
        zoomButton("in");
      } else if (e.key === "-" || e.key === "_") {
        e.preventDefault();
        zoomButton("out");
      } else if (e.key === "0" && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        fitCity();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // zoom helpers close over latest bounds/cam via camRef
  }, [bounds.minX, bounds.minY, bounds.maxX, bounds.maxY]);

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
          if (e.button !== 0 && e.button !== 1 && e.button !== 2) return;
          const target = e.target as Element;
          const onInteractive = !!target.closest?.("[data-node],[data-edge]");
          // Left-click on nodes/roads must stay a click (road/car tools). Pan only from empty map,
          // or with middle/right button / Alt.
          if (e.button === 0 && onInteractive && !e.altKey) {
            drag.current = null;
            didPan.current = false;
            return;
          }
          if (e.button !== 0) e.preventDefault();
          didPan.current = false;
          const live = camRef.current;
          drag.current = {
            px: e.clientX,
            py: e.clientY,
            cx: live.x,
            cy: live.y,
            cw: live.w,
            ch: live.h,
            moved: false,
            live: null,
          };
          // Capture only after the pan threshold — early capture was killing click events.
        }}
        onPointerMove={(e) => {
          if (!drag.current || !svgRef.current) return;
          const dist = Math.hypot(e.clientX - drag.current.px, e.clientY - drag.current.py);
          if (!drag.current.moved && dist < 6) return;
          if (!drag.current.moved) {
            setPanning(true);
            e.currentTarget.setPointerCapture?.(e.pointerId);
            e.preventDefault();
          }
          drag.current.moved = true;
          didPan.current = true;
          const rect = svgRef.current.getBoundingClientRect();
          if (rect.width < 1 || rect.height < 1) return;
          const dx = ((e.clientX - drag.current.px) / rect.width) * drag.current.cw;
          const dy = ((e.clientY - drag.current.py) / rect.height) * drag.current.ch;
          const next = clampCam(
            {
              x: drag.current.cx - dx,
              y: drag.current.cy - dy,
              w: drag.current.cw,
              h: drag.current.ch,
            },
            bounds,
          );
          drag.current.live = next;
          camRef.current = next;
          applyViewBox(svgRef.current, next);
          if (!panRaf.current) {
            panRaf.current = requestAnimationFrame(() => {
              panRaf.current = 0;
              if (drag.current?.live) setCam(drag.current.live);
            });
          }
        }}
        onPointerUp={() => {
          if (panRaf.current) {
            cancelAnimationFrame(panRaf.current);
            panRaf.current = 0;
          }
          if (drag.current?.live) commitCam(drag.current.live);
          drag.current = null;
          setPanning(false);
        }}
        onPointerCancel={() => {
          if (panRaf.current) {
            cancelAnimationFrame(panRaf.current);
            panRaf.current = 0;
          }
          if (drag.current?.live) commitCam(drag.current.live);
          drag.current = null;
          setPanning(false);
        }}
        onContextMenu={(e) => e.preventDefault()}
        onClick={(e) => {
          if (didPan.current) {
            didPan.current = false;
            return;
          }
          if (tool !== "NODE") return;
          if ((e.target as Element).closest("[data-node],[data-edge],[data-car]")) return;
          const { x, y } = clientToWorld(e);
          onAddNode(Math.round(x), Math.round(y));
        }}
        style={{
          cursor: panning
            ? "grabbing"
            : tool === "NODE"
              ? "crosshair"
              : tool === "ROAD" || tool === "TRIP" || tool === "CRASH"
                ? "pointer"
                : "grab",
        }}
      >
        <rect x={cam.x - 40} y={cam.y - 40} width={cam.w + 80} height={cam.h + 80} fill="transparent" />

        {/* Priority corridor overlays under roads */}
        {edges.map((e) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null;
          const status = e.corridorStatus ?? "CLEAR";
          if (status === "CLEAR") return null;
          return (
            <line
              key={`corr-${e.id}`}
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke={status === "LOCKED" ? "rgba(199, 125, 255, 0.55)" : "rgba(255, 200, 87, 0.35)"}
              strokeWidth={status === "LOCKED" ? 14 : 10}
              strokeLinecap="round"
              strokeDasharray={status === "LOCKED" ? "10 8" : "4 10"}
              pointerEvents="none"
            />
          );
        })}

        {/* Live civilian detours (teal) + priority routes (magenta) */}
        {edges.map((e) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null;
          const civ = civilianDetourEdges.has(e.id);
          const pri = priorityRouteEdges.has(e.id);
          if (!civ && !pri) return null;
          return (
            <line
              key={`route-${e.id}`}
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke={pri ? "rgba(199, 125, 255, 0.85)" : "rgba(46, 229, 157, 0.75)"}
              strokeWidth={pri ? 5 : 4}
              strokeLinecap="round"
              strokeDasharray={pri ? "1 0" : "6 5"}
              pointerEvents="none"
              opacity={0.9}
            />
          );
        })}

        {edges.map((e) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null;
          const hit = crashed.has(e.id);
          const selected = selectedEdgeId === e.id;
          const busy = busyEdges.has(e.id);
          const load = (e.occupancy ?? 0) / Math.max(1, e.capacity);
          const locked = (e.corridorStatus ?? "CLEAR") === "LOCKED";
          const alley = e.roadType === "ALLEY";
          const rw = roadWidth(e.roadType, busy, selected, lod.far);
          return (
            <g key={e.id} data-edge={e.id} opacity={alley && lod.far ? 0.35 : 1}>
              <line
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke="transparent"
                strokeWidth={lod.far ? 12 : 18}
                strokeLinecap="round"
                data-edge={e.id}
                style={{ cursor: tool === "CRASH" ? "cell" : "pointer" }}
                onPointerDown={(ev) => {
                  if (ev.button === 0 && !ev.altKey) ev.stopPropagation();
                }}
                onClick={(ev) => {
                  ev.stopPropagation();
                  if (didPan.current) {
                    didPan.current = false;
                    return;
                  }
                  onSelectEdge(e.id);
                }}
              />
              {(load > 0.35 || e.jammed) && (
                <line
                  x1={a.x}
                  y1={a.y}
                  x2={b.x}
                  y2={b.y}
                  stroke={e.jammed ? "rgba(255, 90, 95, 0.32)" : "rgba(255, 107, 111, 0.14)"}
                  strokeWidth={rw + (e.jammed ? 6 : 4)}
                  strokeLinecap="round"
                  pointerEvents="none"
                />
              )}
              <line
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke={locked ? "#5a4a66" : roadStroke(e, hit, selected, busy)}
                strokeWidth={rw}
                strokeLinecap="round"
                pointerEvents="none"
                opacity={locked ? 0.55 : 1}
              />
              {lod.showEdgeTags && locked && (
                <text
                  x={(a.x + b.x) / 2}
                  y={(a.y + b.y) / 2 - 8}
                  textAnchor="middle"
                  fill="#c77dff"
                  fontSize="9"
                  fontWeight="700"
                  pointerEvents="none"
                >
                  LOCK
                </text>
              )}
              {lod.showEdgeTags && e.jammed && !locked && (
                <text
                  x={(a.x + b.x) / 2}
                  y={(a.y + b.y) / 2 + 10}
                  textAnchor="middle"
                  fill="#ff6b6f"
                  fontSize="8"
                  fontWeight="600"
                  pointerEvents="none"
                >
                  JAM
                </text>
              )}
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
            // Dense overview: skip green housings; keep red/yellow as signal posts.
            if (lod.dense && lod.far && e.lightColor === "GREEN") return null;
            const a = byId.get(e.from);
            const b = byId.get(e.to);
            if (!a || !b) return null;
            const fill = signalFill(e.lightColor);
            if (!fill) return null;
            const p = approachPoint(a, b);
            const scale = lod.far ? 0.78 : 1;
            const hw = 4.2 * scale;
            const hh = 11 * scale;
            return (
              <g key={`light-${e.id}`} pointerEvents="none" transform={`translate(${p.x}, ${p.y})`}>
                {/* Vertical signal head — square housing, not a car-like blob */}
                <rect
                  x={-hw / 2}
                  y={-hh / 2}
                  width={hw}
                  height={hh}
                  rx={1.2}
                  fill="#12161b"
                  stroke="#c5d0da"
                  strokeWidth={0.9}
                  opacity={0.95}
                />
                <circle cx={0} cy={e.lightColor === "YELLOW" ? 0 : e.lightColor === "RED" ? -hh * 0.22 : hh * 0.22} r={2.1 * scale} fill={fill} />
                {e.lightColor === "RED" && (
                  <circle cx={0} cy={-hh * 0.22} r={3.4 * scale} fill="none" stroke={fill} strokeWidth={0.7} opacity={0.55} />
                )}
              </g>
            );
          })}

        {lod.showOdLines &&
          vehicles.map((v) => {
            const o = byId.get(v.origin);
            const d = byId.get(v.destination);
            if (!o || !d || v.arrived) return null;
            if (!isPriority(v.serviceClass)) return null;
            const color = serviceCarColor(v.serviceClass, v.id);
            return (
              <g key={`od-${v.id}`} pointerEvents="none" opacity={0.28}>
                <line x1={o.x} y1={o.y} x2={d.x} y2={d.y} stroke={color} strokeWidth={1.5} strokeDasharray="5 6" />
              </g>
            );
          })}

        {nodes.map((n) => {
          if (!inView(n.x, n.y, cam, 60)) return null;
          const isTripStart = tripFrom === n.id;
          const isRoadStart = selectedFrom === n.id;
          const isSelected = selectedNodeId === n.id;
          const facility = facilityStroke(n.facility);
          const active = isTripStart || isRoadStart || isSelected;
          const showLabel =
            active || !!facility || lod.showNodeLabels || (tool !== "TRIP" && !lod.dense && !animateTravel);
          const r = lod.far && !active && !facility ? (lod.dense ? 3.2 : 6) : active ? 14 : 10;
          return (
            <g
              key={n.id}
              data-node={n.id}
              onPointerDown={(ev) => {
                // Keep node presses from starting a background pan / edge hit.
                if (ev.button === 0 && !ev.altKey) ev.stopPropagation();
              }}
              onClick={(ev) => {
                ev.stopPropagation();
                if (didPan.current) {
                  didPan.current = false;
                  return;
                }
                onSelectNode(n.id);
              }}
              style={{ cursor: "pointer" }}
            >
              {/* Generous hit disc — dense LOD makes visible nodes tiny */}
              <circle cx={n.x} cy={n.y} r={Math.max(r + 6, 14)} fill="transparent" />
              <circle
                cx={n.x}
                cy={n.y}
                r={r}
                fill={isTripStart ? "#0f2e26" : isSelected ? "#1c2833" : "#151a1f"}
                stroke={
                  isSelected
                    ? "#ff7a7e"
                    : isTripStart
                      ? "#2ee59d"
                      : isRoadStart
                        ? "#4cc9f0"
                        : facility ?? (lod.far ? "#4a5560" : "#7d8b99")
                }
                strokeWidth={active || facility ? 2.4 : lod.far ? 1.2 : 1.8}
              />
              {showLabel && (
                <text
                  x={n.x}
                  y={n.y + (r > 8 ? 3 : 2)}
                  textAnchor="middle"
                  fill="#d7e0e8"
                  fontSize={lod.far ? "7" : "9"}
                  fontWeight="600"
                  pointerEvents="none"
                  opacity={facility || active ? 1 : 0.85}
                >
                  {n.label}
                </text>
              )}
            </g>
          );
        })}

        {parkBuild &&
          [...parkedByNode.entries()].map(([nodeId, count]) => {
            const n = byId.get(nodeId);
            if (!n || !inView(n.x, n.y, cam) || count < 1) return null;
            return (
              <g key={`park-${nodeId}`} pointerEvents="none">
                <circle cx={n.x + 10} cy={n.y - 10} r={9} fill="#0f1419" stroke="#4cc9f0" strokeWidth={1.4} />
                <text
                  x={n.x + 10}
                  y={n.y - 7}
                  textAnchor="middle"
                  fill="#7ad7f0"
                  fontSize="8"
                  fontWeight="800"
                  fontFamily="Outfit, sans-serif"
                >
                  {count > 99 ? "99+" : count}
                </text>
              </g>
            );
          })}

        {vehicles.map((v) => {
          if (v.arrived) return null;
          const priority = isPriority(v.serviceClass);
          // Dense build: hide civilian pile-ups; keep VIP/emergency visible.
          if (parkBuild && !priority) return null;
          const pos = carPosition(v, byId, edges, tickFrac);
          if (!pos || !inView(pos.x, pos.y, cam)) return null;
          return (
            <CarSprite
              key={`car-${v.id}`}
              v={v}
              x={pos.x}
              y={pos.y}
              angle={pos.angle}
              compact={lod.civilianDots && !priority}
              showLabel={priority || (lod.near && activeCars <= 40)}
            />
          );
        })}

        <text x={cam.x + 16} y={cam.y + 22} fill="#8b98a6" fontSize="12">
          {animateTravel
            ? lod.dense
              ? "Drag to pan · scroll or +/− to zoom · cars = arrows · lights = posts"
              : "Live · drag pan · scroll or +/− zoom"
            : tool === "NODE"
              ? "Click to add · drag to pan · +/− or scroll to zoom"
              : tool === "ROAD"
                ? selectedFrom == null
                  ? "Road: click START node, then END · drag empty space to pan"
                  : `Road from node ${selectedFrom} · click END node`
                : tool === "CRASH"
                  ? "Click a road to close it (live detour)"
                  : parkBuild
                    ? `${activeCars} cars ready — blue badges = queue size · Start race to move`
                    : tripFrom == null
                      ? "Car: click START node, then END · drag empty space to pan"
                      : `Car from node ${tripFrom} · click END node`}
        </text>
      </svg>

      <div className="map-nav" role="group" aria-label="Map zoom">
        <button type="button" className="map-nav-btn" aria-label="Zoom in" title="Zoom in" disabled={!canZoomIn} onClick={() => zoomButton("in")}>
          +
        </button>
        <button type="button" className="map-nav-btn" aria-label="Zoom out" title="Zoom out" disabled={!canZoomOut} onClick={() => zoomButton("out")}>
          −
        </button>
        <button type="button" className="map-nav-btn map-nav-fit" aria-label="Fit city" title="Fit city in view" onClick={fitCity}>
          ⊡
        </button>
      </div>

      <svg
        className="minimap"
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        aria-label="Minimap — click to jump"
        onClick={(e) => {
          const svg = e.currentTarget;
          const rect = svg.getBoundingClientRect();
          if (rect.width < 1 || rect.height < 1) return;
          const mx = ((e.clientX - rect.left) / rect.width) * VIEW_W;
          const my = ((e.clientY - rect.top) / rect.height) * VIEW_H;
          const prev = camRef.current;
          commitCam({ x: mx - prev.w / 2, y: my - prev.h / 2, w: prev.w, h: prev.h });
        }}
      >
        <rect x={0} y={0} width={VIEW_W} height={VIEW_H} fill="#0f1419" />
        {edges.map((e) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null;
          if (lod.dense && e.roadType === "ALLEY") return null;
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
        {!lod.dense &&
          nodes.map((n) => <circle key={n.id} cx={n.x} cy={n.y} r={3} fill="#8b98a6" />)}
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
