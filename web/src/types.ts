export type SessionMode = "BUILD" | "PLAY";

export type NodeDto = {
  id: number;
  label: string;
  x: number;
  y: number;
};

export type EdgeDto = {
  id: number;
  from: number;
  to: number;
  baseWeight: number;
  capacity: number;
};

export type VehicleDto = {
  id: number;
  destination: number;
  fuel: number;
  fuelBurned: number;
  arrived: boolean;
  replanCount: number;
  positionType: "AT_NODE" | "ON_EDGE";
  nodeId: number | null;
  edgeId: number | null;
  ticksRemaining: number | null;
};

export type AccidentDto = {
  id: string;
  edgeId: number;
  caption: string;
  ticksRemaining: number;
  showCross: boolean;
};

export type SessionSnapshot = {
  mode: SessionMode;
  worldTick: number;
  hasUnappliedEdits: boolean;
  nodeCount: number;
  edgeCount: number;
  arrivedCount: number;
  fleetSize: number;
  nodes: NodeDto[];
  edges: EdgeDto[];
  vehicles: VehicleDto[];
  accidents: AccidentDto[];
};
