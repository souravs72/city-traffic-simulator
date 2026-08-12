export type SessionMode = "BUILD" | "PLAY";

export type CityPreset = "BLANK" | "PLAYGROUND" | "DOWNTOWN" | "MEGACITY";

export type ClickTool = "ROAD" | "TRIP" | "CRASH";

export type RoadType = "HIGHWAY" | "AVENUE" | "ALLEY";

export type FacilityKind = "NONE" | "HOSPITAL" | "POLICE_STATION" | "FIRE_STATION" | "VIP_SITE";

export type ServiceClass = "CIVILIAN" | "VIP" | "POLICE" | "AMBULANCE" | "FIRE";

export type ControlPolicy = "CITY_FLOW" | "MAPS_LIKE";

export type NodeDto = {
  id: number;
  label: string;
  x: number;
  y: number;
  facility?: FacilityKind | string | null;
};

export type EdgeDto = {
  id: number;
  from: number;
  to: number;
  baseWeight: number;
  capacity: number;
  occupancy: number;
  roadType: RoadType | string;
  lightColor: "GREEN" | "YELLOW" | "RED" | null;
};

export type VehicleDto = {
  id: number;
  name: string;
  origin: number;
  destination: number;
  fuel: number;
  fuelBurned: number;
  arrived: boolean;
  replanCount: number;
  positionType: "AT_NODE" | "ON_EDGE";
  nodeId: number | null;
  edgeId: number | null;
  ticksRemaining: number | null;
  plannedShortestTicks: number;
  plannedLiveTicks: number;
  spawnedAtTick: number;
  arrivedAtTick: number | null;
  actualTicks: number | null;
  remainingShortestEta: number | null;
  remainingLiveEta: number | null;
  serviceClass?: ServiceClass | string | null;
  scheduledDepartAtTick?: number;
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
  controlPolicy?: ControlPolicy | string;
  nodes: NodeDto[];
  edges: EdgeDto[];
  vehicles: VehicleDto[];
  accidents: AccidentDto[];
};
