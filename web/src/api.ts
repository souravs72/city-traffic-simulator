import type { SessionSnapshot } from "./types";

async function parse<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new Error((data as { error?: string }).error ?? res.statusText);
  }
  return data as T;
}

export async function createSession(body?: {
  rows?: number;
  cols?: number;
  fleetSize?: number;
  seed?: number;
  initialFuel?: number;
  maxTicks?: number;
}): Promise<SessionSnapshot> {
  const res = await fetch("/api/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      rows: body?.rows ?? 8,
      cols: body?.cols ?? 8,
      fleetSize: body?.fleetSize ?? 10,
      seed: body?.seed ?? 42,
      initialFuel: body?.initialFuel ?? 200,
      maxTicks: body?.maxTicks ?? 300,
    }),
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

export async function connectEdge(input: {
  from: number;
  to: number;
  capacity?: number;
  twoWay?: boolean;
}): Promise<SessionSnapshot> {
  return parse(
    await fetch("/api/city/edges", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        from: input.from,
        to: input.to,
        capacity: input.capacity ?? 2,
        twoWay: input.twoWay ?? false,
      }),
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
