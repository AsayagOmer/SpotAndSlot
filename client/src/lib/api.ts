// ─────────────────────────────────────────────────────────────────────────────
// Smart Parking API client
//
// Connects the Spot-Insight GUI to the Smart Parking REST API.
// All endpoints, object shapes and command names mirror the server contract
// (package `ambient_invisible_intelligence`, base path `/ambient-invisible-intelligence`).
// ─────────────────────────────────────────────────────────────────────────────

const DEFAULT_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8084/ambient-invisible-intelligence";
const SYSTEM_ID = import.meta.env.VITE_SYSTEM_ID ?? "2026b.Omer.Asayag";
const API_VERSION = import.meta.env.VITE_API_VERSION ?? "1.4";
const SERVER_KEY = "spot-insight.server-base";

// ── Server address ───────────────────────────────────────────────────────────
// On a phone "localhost" is the phone itself, so the mobile app lets the user
// point at the API server (e.g. http://192.168.1.20:8084/...). The choice is
// persisted; the default comes from the build-time env.
function loadServerBase(): string {
  try {
    return localStorage.getItem(SERVER_KEY) || DEFAULT_BASE_URL;
  } catch {
    return DEFAULT_BASE_URL;
  }
}

let BASE_URL = loadServerBase();

export function getServerBase(): string {
  return BASE_URL;
}

export function setServerBase(url: string | null): void {
  const clean = url?.trim().replace(/\/+$/, "") ?? "";
  BASE_URL = clean || DEFAULT_BASE_URL;
  try {
    if (clean) localStorage.setItem(SERVER_KEY, BASE_URL);
    else localStorage.removeItem(SERVER_KEY);
  } catch {
    /* ignore */
  }
}

// Running inside the Capacitor (Android) shell?
function isNative(): boolean {
  const cap = (window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
  return Boolean(cap?.isNativePlatform?.());
}

// URL for the ML prediction service. In web dev the Vite proxy forwards
// /ml → localhost:5000; the native app has no proxy, so it calls the service
// directly on the API server's host.
export function mlUrl(path: string): string {
  if (!isNative()) return `/ml${path}`;
  try {
    const u = new URL(BASE_URL);
    return `${u.protocol}//${u.hostname}:5000${path}`;
  } catch {
    return `/ml${path}`;
  }
}

export const apiConfig = {
  get BASE_URL() {
    return BASE_URL;
  },
  SYSTEM_ID,
  API_VERSION,
};

// ── Current identity ─────────────────────────────────────────────────────────
// Set by the auth provider after login. Sprint 4 authenticates every API call
// with userSystemID / userEmail / userPassword request parameters (creation and
// commands carry only userPassword), so the signed-in password is kept too.
const FALLBACK_USER_EMAIL = import.meta.env.VITE_USER_EMAIL ?? "";
let currentUserEmail: string = FALLBACK_USER_EMAIL;
let currentUserPassword: string = "";

export function setCurrentUser(email: string | null, password: string | null): void {
  currentUserEmail = email ?? FALLBACK_USER_EMAIL;
  currentUserPassword = password ?? "";
}

export function getCurrentUserEmail(): string {
  return currentUserEmail;
}

// the credential trio attached to Sprint 4 authenticated requests
function creds(): URLSearchParams {
  return new URLSearchParams({
    userSystemID: SYSTEM_ID,
    userEmail: currentUserEmail,
    userPassword: currentUserPassword,
  });
}

// large page so list views (map, admin tables) see the whole dataset
const LIST_PAGING = "size=1000&page=0";

// ── Boundary types (match the server's JSON shapes) ──────────────────────────

export interface ObjectId {
  objectId: string;
  systemID: string;
}

export interface ObjectBoundary {
  id?: ObjectId;
  type: string;
  alias?: string;
  status?: string;
  active?: boolean;
  creationTimestamp?: string;
  createdBy?: { userId: { email: string; systemID: string } };
  location?: { lat: number; lng: number };
  objectDetails?: Record<string, unknown>;
}

export interface CommandBoundary {
  command: string;
  targetObject: { id: ObjectId };
  invokedBy: { userId: { email: string; systemID: string } };
  commandAttributes?: Record<string, unknown>;
}

// Domain object type constants (mirror server ObjectType)
export const ObjectType = {
  PARKING_LOT: "PARKING_LOT",
  PARKING_SECTION: "PARKING_SECTION",
  PARKING_SLOT: "PARKING_SLOT",
  VEHICLE: "VEHICLE",
} as const;

// Typed view over a PARKING_SLOT's objectDetails
export interface SlotDetails {
  parentLotId?: string;
  parentSectionId?: string;
  slotKind?: "REGULAR" | "HANDICAP";
  healthStatus?: "OK" | "FAULTY";
  lastHeartbeat?: number;
}

// ── Low-level fetch helpers ──────────────────────────────────────────────────

function headers(): HeadersInit {
  return {
    "Content-Type": "application/json",
    "API-Version": API_VERSION,
  };
}

// Thrown when the server responds with a non-OK HTTP status. `status` lets
// callers tell an authentication failure (401) apart from other server errors —
// and, crucially, apart from a network/connectivity failure, which rejects with
// a plain TypeError (not an ApiError) instead.
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let detail = "";
    try {
      detail = await res.text();
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, `API ${res.status} ${res.statusText}${detail ? `: ${detail}` : ""}`);
  }
  // Some endpoints (PUT, DELETE) return no body
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// ── Objects ──────────────────────────────────────────────────────────────────

export async function getAllObjects(): Promise<ObjectBoundary[]> {
  const res = await fetch(`${BASE_URL}/objects?${creds()}&${LIST_PAGING}`, { headers: headers() });
  return handle<ObjectBoundary[]>(res);
}

export async function getObject(objectId: string): Promise<ObjectBoundary> {
  const res = await fetch(`${BASE_URL}/objects/${SYSTEM_ID}/${objectId}?${creds()}`, { headers: headers() });
  return handle<ObjectBoundary>(res);
}

// Create a parking object as the signed-in admin (server enforces ADMIN + lot ownership).
export async function createObject(input: ObjectBoundary): Promise<ObjectBoundary> {
  const body: ObjectBoundary = {
    ...input,
    createdBy: input.createdBy ?? { userId: { email: currentUserEmail, systemID: SYSTEM_ID } },
  };
  const res = await fetch(`${BASE_URL}/objects?${new URLSearchParams({ userPassword: currentUserPassword })}`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(body),
  });
  return handle<ObjectBoundary>(res);
}

export async function updateObject(objectId: string, update: Partial<ObjectBoundary>): Promise<void> {
  const res = await fetch(`${BASE_URL}/objects/${SYSTEM_ID}/${objectId}?${creds()}`, {
    method: "PUT",
    headers: headers(),
    body: JSON.stringify(update),
  });
  await handle<void>(res);
}

// Delete an object; the server cascades (lot -> sections + slots, section -> slots).
export async function deleteObject(objectId: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/objects/${SYSTEM_ID}/${objectId}?${creds()}`, {
    method: "DELETE",
    headers: headers(),
  });
  await handle<void>(res);
}

// ── Commands ─────────────────────────────────────────────────────────────────

export async function invokeCommand(
  command: string,
  targetObjectId: string,
  commandAttributes: Record<string, unknown> = {},
): Promise<unknown[]> {
  const body: CommandBoundary = {
    command,
    targetObject: { id: { objectId: targetObjectId, systemID: SYSTEM_ID } },
    invokedBy: { userId: { email: currentUserEmail, systemID: SYSTEM_ID } },
    commandAttributes,
  };
  const res = await fetch(`${BASE_URL}/commands?${new URLSearchParams({ userPassword: currentUserPassword })}`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(body),
  });
  return handle<unknown[]>(res);
}

// ── Domain-specific convenience wrappers ─────────────────────────────────────

export interface LotAvailability {
  lotId: string;
  freeSlots: number;
  searchingDrivers: number;
  worthEntering: boolean;
}

export async function availabilityInLot(lotId: string, slotKindFilter?: string): Promise<LotAvailability> {
  const attrs = slotKindFilter ? { slotKindFilter } : {};
  const [result] = (await invokeCommand("availabilityInLot", lotId, attrs)) as LotAvailability[];
  return result;
}

export interface SectionAvailability {
  sectionId: string;
  freeSlots: number;
  totalSlots: number;
}

export async function availabilityInSection(sectionId: string): Promise<SectionAvailability> {
  const [result] = (await invokeCommand("availabilityInSection", sectionId, {})) as SectionAvailability[];
  return result;
}

export interface PredictionResult {
  lotId: string;
  targetTimestamp?: string | number;
  predictedFreeSlots: number;
  note?: string;
}

export async function predictAvailability(
  lotId: string,
  targetTimestamp: string,
): Promise<PredictionResult> {
  const [result] = (await invokeCommand("predictAvailability", lotId, { targetTimestamp })) as PredictionResult[];
  return result;
}

export async function updateSlotState(slotId: string, newState: "FREE" | "OCCUPIED"): Promise<void> {
  await invokeCommand("updateSlotState", slotId, { newState });
}

export interface BestSectionResult {
  lotId: string;
  bestSectionId: string | null;
  freeSlots: number;
  perSection: Record<string, number>;
}

export async function bestSectionInLot(lotId: string): Promise<BestSectionResult> {
  const [result] = (await invokeCommand("bestSectionInLot", lotId, {})) as BestSectionResult[];
  return result;
}

// ── Users / auth ─────────────────────────────────────────────────────────────

export interface UserBoundary {
  userId: { email: string; systemID: string };
  role: string;
  username?: string;
  avatar?: string;
}

// Stateless login: returns the user's boundary (role included) or throws on 401.
export async function loginUser(email: string, password: string): Promise<UserBoundary> {
  const params = new URLSearchParams({ password });
  const res = await fetch(
    `${BASE_URL}/users/login/${SYSTEM_ID}/${encodeURIComponent(email)}?${params}`,
    { headers: headers() },
  );
  return handle<UserBoundary>(res);
}

export interface NewUserInput {
  email: string;
  password: string;
  username?: string;
  avatar?: string;
}

// Public self sign-up — always creates an END_USER account.
export async function signUp(input: NewUserInput): Promise<UserBoundary> {
  const res = await fetch(`${BASE_URL}/users`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ ...input, role: "END_USER" }),
  });
  return handle<UserBoundary>(res);
}

// Create a user with an elevated role; the server requires the acting admin.
export async function createUserAsAdmin(
  input: NewUserInput & { role: string },
): Promise<UserBoundary> {
  const params = new URLSearchParams({ actingEmail: currentUserEmail });
  const res = await fetch(`${BASE_URL}/users?${params}`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(input),
  });
  return handle<UserBoundary>(res);
}

// ── Admin endpoints ───────────────────────────────────────────────────────────

export async function getAllUsers(): Promise<UserBoundary[]> {
  const res = await fetch(`${BASE_URL}/admin/users?${creds()}&${LIST_PAGING}`, { headers: headers() });
  return handle<UserBoundary[]>(res);
}

export interface CommandHistoryItem {
  id?: { commandId: string; systemID: string };
  command: string;
  targetObject?: { id: ObjectId };
  invocationTimestamp?: string;
  invokedBy?: { userId: { email: string; systemID: string } };
  commandAttributes?: Record<string, unknown>;
}

export async function getAllCommands(): Promise<CommandHistoryItem[]> {
  const res = await fetch(`${BASE_URL}/admin/commands?${creds()}&${LIST_PAGING}`, { headers: headers() });
  return handle<CommandHistoryItem[]>(res);
}

// Run a lot-level health check (UC-10). Returns any slots with non-OK health.
export interface FaultySlot {
  slotId: string;
  alias: string;
  healthStatus: string;
}

export async function runHealthCheck(lotId: string): Promise<FaultySlot[]> {
  return (await invokeCommand("runHealthCheck", lotId, {})) as FaultySlot[];
}
