import type { CityPreset, SessionSnapshot } from "./types";

/** Callers: App.tsx and map tools. Adds optional VITE_API_KEY as X-Api-Key. */
const API_KEY = (import.meta.env.VITE_API_KEY as string | undefined)?.trim();

const SESSION_KEY = "cityflow.sessionId";

export function getSessionId(): string {
  let id = sessionStorage.getItem(SESSION_KEY);
  if (!id) {
    id = "web-" + Math.random().toString(36).slice(2, 10);
    sessionStorage.setItem(SESSION_KEY, id);
  }
  return id;
}

function apiHeaders(json = false): HeadersInit {
  const headers: Record<string, string> = {};
  if (json) {
    headers["Content-Type"] = "application/json";
  }
  if (API_KEY) {
    headers["X-Api-Key"] = API_KEY;
  }
  headers["X-Session-Id"] = getSessionId();
  return headers;
}

async function parse<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new Error((data as { error?: string }).error ?? res.statusText);
  }
  return data as T;
}

async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const base = apiHeaders(false);
  for (const [k, v] of Object.entries(base)) {
    if (!headers.has(k)) {
      headers.set(k, v);
    }
  }
  return parse(await fetch(path, { ...init, headers }));
}

export async function newCity(): Promise<SessionSnapshot> {
  return apiFetch("/api/session/new", { method: "POST" });
}

export async function createSession(body?: {
  preset?: CityPreset;
  rows?: number;
  cols?: number;
  fleetSize?: number;
  seed?: number;
  initialFuel?: number;
  maxTicks?: number;
  replaceSaved?: boolean;
}): Promise<SessionSnapshot> {
  const payload = {
    preset: body?.preset ?? "PLAYGROUND",
    seed: body?.seed ?? 42,
    maxTicks: body?.maxTicks ?? 300,
    fleetSize: body?.fleetSize ?? 0,
    replaceSaved: body?.replaceSaved ?? false,
    ...(body?.initialFuel != null ? { initialFuel: body.initialFuel } : {}),
    ...(body?.rows != null ? { rows: body.rows } : {}),
    ...(body?.cols != null ? { cols: body.cols } : {}),
  };

  return apiFetch("/api/session", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify(payload),
  });
}

export async function getSession(): Promise<SessionSnapshot> {
  return apiFetch("/api/session");
}

export async function buildMode(): Promise<SessionSnapshot> {
  return apiFetch("/api/session/build", { method: "POST" });
}

export async function playMode(): Promise<SessionSnapshot> {
  return apiFetch("/api/session/play", { method: "POST" });
}

export async function applyEdits(): Promise<SessionSnapshot> {
  return apiFetch("/api/session/apply", { method: "POST" });
}

export async function stepTick(): Promise<SessionSnapshot> {
  return apiFetch("/api/session/step", { method: "POST" });
}

export async function runTicks(ticks: number): Promise<SessionSnapshot> {
  return apiFetch("/api/session/run", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ ticks }),
  });
}

export async function addNode(input: {
  x: number;
  y: number;
  label?: string;
  facility?: string;
}): Promise<SessionSnapshot> {
  return apiFetch("/api/city/nodes", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify(input),
  });
}

export async function setFacility(nodeId: number, facility: string): Promise<SessionSnapshot> {
  return apiFetch("/api/city/facility", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ nodeId, facility }),
  });
}

export async function setPolicy(policy: string): Promise<SessionSnapshot> {
  return apiFetch("/api/session/policy", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ policy }),
  });
}

export async function deleteNode(id: number): Promise<SessionSnapshot> {
  return apiFetch("/api/city/nodes/delete", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ id }),
  });
}

export async function connectEdge(input: {
  from: number;
  to: number;
  capacity?: number;
  twoWay?: boolean;
  roadType?: string;
}): Promise<SessionSnapshot> {
  return apiFetch("/api/city/edges", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({
      from: input.from,
      to: input.to,
      capacity: input.capacity ?? 0,
      twoWay: input.twoWay ?? true,
      roadType: input.roadType ?? "AVENUE",
    }),
  });
}

export async function deleteEdge(id: number): Promise<SessionSnapshot> {
  return apiFetch("/api/city/edges/delete", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ id }),
  });
}

export async function spawnAccident(input: {
  edgeId: number;
  durationTicks?: number;
  caption?: string;
}): Promise<SessionSnapshot> {
  return apiFetch("/api/accidents", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify(input),
  });
}

export async function addTrip(
  from: number,
  to: number,
  name?: string,
  serviceClass = "CIVILIAN",
  scheduledDepartAtTick = 0,
): Promise<SessionSnapshot> {
  return apiFetch("/api/fleet/trips", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({
      from,
      to,
      name: name && name.trim() ? name.trim() : null,
      serviceClass,
      scheduledDepartAtTick,
    }),
  });
}

export async function addRandomTrip(): Promise<SessionSnapshot> {
  return apiFetch("/api/fleet/trips/random", { method: "POST" });
}

export async function addRushHour(count = 8): Promise<SessionSnapshot> {
  return apiFetch("/api/fleet/rush", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ count }),
  });
}

export async function dispatchEmergency(serviceClass: string, sceneNodeId: number): Promise<SessionSnapshot> {
  return apiFetch("/api/fleet/dispatch", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ serviceClass, sceneNodeId }),
  });
}

export async function vipConvoy(input: {
  from: number;
  to: number;
  departAtTick?: number;
  escorts?: number;
}): Promise<SessionSnapshot> {
  return apiFetch("/api/fleet/vip-convoy", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({
      from: input.from,
      to: input.to,
      departAtTick: input.departAtTick ?? 8,
      escorts: input.escorts ?? 2,
    }),
  });
}

export type PolicyCompare = {
  ticks: number;
  fleetSize: number;
  mapsLike: {
    policy: string;
    emergencyArrivalTicks: number;
    civilianAvgTicks: number;
    fleetAvgTicks: number;
    arrived: number;
    stranded: number;
    fleetP90?: number;
    civilianP90?: number;
    emergencyP90?: number;
    jainCivilian?: number;
  };
  cityFlow: {
    policy: string;
    emergencyArrivalTicks: number;
    civilianAvgTicks: number;
    fleetAvgTicks: number;
    arrived: number;
    stranded: number;
    fleetP90?: number;
    civilianP90?: number;
    emergencyP90?: number;
    jainCivilian?: number;
  };
  cityFlowWinsEmergency: boolean;
  cityFlowCivilianFair: boolean;
  verdict: string;
  mapsFleetP90?: number;
  cityFlowFleetP90?: number;
  mapsJainCivilian?: number;
  cityFlowJainCivilian?: number;
  cityFlowEmergencyP90?: number;
  mapsEmergencyP90?: number;
};

export async function comparePolicies(ticks = 80): Promise<PolicyCompare> {
  return apiFetch("/api/session/compare", {
    method: "POST",
    headers: apiHeaders(true),
    body: JSON.stringify({ ticks }),
  });
}

export async function seedFacilities(): Promise<SessionSnapshot> {
  return apiFetch("/api/city/facilities/seed", { method: "POST" });
}
