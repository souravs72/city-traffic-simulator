import type { CityPreset, SessionSnapshot } from "./types";

async function parse<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new Error((data as { error?: string }).error ?? res.statusText);
  }
  return data as T;
}

export async function newCity(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/session/new", { method: "POST" }));
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

  const res = await fetch("/api/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return parse(res);
}

export async function getSession(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/session"));
}

export async function buildMode(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/session/build", { method: "POST" }));
}

export async function playMode(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/session/play", { method: "POST" }));
}

export async function applyEdits(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/session/apply", { method: "POST" }));
}

export async function stepTick(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/session/step", { method: "POST" }));
}

export async function runTicks(ticks: number): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/session/run", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ticks }),
    }),
  );
}

export async function addNode(input: {
  x: number;
  y: number;
  label?: string;
  facility?: string;
}): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/city/nodes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  );
}

export async function setFacility(nodeId: number, facility: string): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/city/facility", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nodeId, facility }),
    }),
  );
}

export async function setPolicy(policy: string): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/session/policy", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ policy }),
    }),
  );
}

export async function deleteNode(id: number): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/city/nodes/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id }),
    }),
  );
}

export async function connectEdge(input: {
  from: number;
  to: number;
  capacity?: number;
  twoWay?: boolean;
  roadType?: string;
}): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/city/edges", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        from: input.from,
        to: input.to,
        capacity: input.capacity ?? 0,
        twoWay: input.twoWay ?? true,
        roadType: input.roadType ?? "AVENUE",
      }),
    }),
  );
}

export async function deleteEdge(id: number): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/city/edges/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id }),
    }),
  );
}

export async function spawnAccident(input: {
  edgeId: number;
  durationTicks?: number;
  caption?: string;
}): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/accidents", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  );
}

export async function addTrip(
  from: number,
  to: number,
  name?: string,
  serviceClass = "CIVILIAN",
  scheduledDepartAtTick = 0,
): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/fleet/trips", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        from,
        to,
        name: name && name.trim() ? name.trim() : null,
        serviceClass,
        scheduledDepartAtTick,
      }),
    }),
  );
}

export async function addRandomTrip(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/fleet/trips/random", { method: "POST" }));
}

export async function addRushHour(count = 8): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/fleet/rush", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ count }),
    }),
  );
}

export async function dispatchEmergency(serviceClass: string, sceneNodeId: number): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/fleet/dispatch", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ serviceClass, sceneNodeId }),
    }),
  );
}

export async function vipConvoy(input: {
  from: number;
  to: number;
  departAtTick?: number;
  escorts?: number;
}): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/fleet/vip-convoy", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        from: input.from,
        to: input.to,
        departAtTick: input.departAtTick ?? 8,
        escorts: input.escorts ?? 2,
      }),
    }),
  );
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
  };
  cityFlow: {
    policy: string;
    emergencyArrivalTicks: number;
    civilianAvgTicks: number;
    fleetAvgTicks: number;
    arrived: number;
    stranded: number;
  };
  cityFlowWinsEmergency: boolean;
  cityFlowCivilianFair: boolean;
  verdict: string;
};

export async function comparePolicies(ticks = 80): Promise<PolicyCompare> {
  return parse(
    await fetch("/api/session/compare", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ticks }),
    }),
  );
}

export async function seedFacilities(): Promise<SessionSnapshot> {
  return parse(await fetch("/api/city/facilities/seed", { method: "POST" }));
}
