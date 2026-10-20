import { useQueries, useQuery } from "@tanstack/react-query";
import {
  getAllObjects,
  getAllUsers,
  getAllCommands,
  predictAvailability,
  ObjectType,
  mlUrl,
  type ObjectBoundary,
  type SlotDetails,
} from "@/lib/api";
import { useSelectedLot } from "@/lib/selectedLot";

// GUI-facing status model used by <ParkingSpot />
export type SpotStatus =
  | "available"
  | "occupied"
  | "disabled-available"
  | "disabled-occupied"
  | "saved";

export interface Spot {
  id: string; // alias, e.g. "A-1"
  objectId: string; // server object id (for commands)
  status: SpotStatus;
}

export interface SectionGroup {
  sectionId: string;
  alias: string;
  spots: Spot[];
}

export interface ParkingData {
  lotId: string | null;
  lotAlias: string | null;
  sections: SectionGroup[];
  totalSpots: number;
  occupiedCount: number;
  freeCount: number;
  occupancyRate: number;
}

// Map a server PARKING_SLOT object to the GUI spot model
function toSpot(obj: ObjectBoundary): Spot {
  const det = (obj.objectDetails ?? {}) as SlotDetails;
  const handicap = det.slotKind === "HANDICAP";
  const occupied = obj.status === "OCCUPIED";
  let status: SpotStatus;
  if (handicap) status = occupied ? "disabled-occupied" : "disabled-available";
  else status = occupied ? "occupied" : "available";
  return {
    id: obj.alias ?? obj.id?.objectId ?? "?",
    objectId: obj.id?.objectId ?? "",
    status,
  };
}

// Natural sort so "A-2" comes before "A-10"
function naturalCompare(a: string, b: string): number {
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: "base" });
}

// Exported for unit tests (pure function: API objects -> GUI model).
export function shapeParkingData(objects: ObjectBoundary[], selectedLotId: string | null): ParkingData {
  const lots = objects.filter((o) => o.type === ObjectType.PARKING_LOT);
  const sections = objects.filter((o) => o.type === ObjectType.PARKING_SECTION);
  const slots = objects.filter((o) => o.type === ObjectType.PARKING_SLOT && o.active !== false);

  // shape the lot the user selected; fall back to the first lot
  const lot = lots.find((l) => l.id?.objectId === selectedLotId) ?? lots[0] ?? null;
  const lotId = lot?.id?.objectId ?? null;

  // Group slots by their parent section
  const sectionGroups: SectionGroup[] = sections
    .filter((sec) => {
      // keep sections that belong to the chosen lot (or all if no lot)
      const det = (sec.objectDetails ?? {}) as { parentLotId?: string };
      return !lotId || det.parentLotId === lotId;
    })
    .map((sec) => {
      const secId = sec.id?.objectId ?? "";
      const secSpots = slots
        .filter((s) => (s.objectDetails as SlotDetails)?.parentSectionId === secId)
        .map(toSpot)
        .sort((a, b) => naturalCompare(a.id, b.id));
      return { sectionId: secId, alias: sec.alias ?? "Section", spots: secSpots };
    })
    .filter((g) => g.spots.length > 0)
    .sort((a, b) => naturalCompare(a.alias, b.alias));

  const allSpots = sectionGroups.flatMap((g) => g.spots);
  const totalSpots = allSpots.length;
  const occupiedCount = allSpots.filter(
    (s) => s.status === "occupied" || s.status === "disabled-occupied",
  ).length;
  const freeCount = totalSpots - occupiedCount;
  const occupancyRate = totalSpots > 0 ? Math.round((occupiedCount / totalSpots) * 100) : 0;

  return {
    lotId,
    lotAlias: lot?.alias ?? null,
    sections: sectionGroups,
    totalSpots,
    occupiedCount,
    freeCount,
    occupancyRate,
  };
}

// Live parking data for the currently selected lot, polled from the API
export function useParkingData(pollMs = 5000) {
  const { selectedLotId } = useSelectedLot();
  const query = useQuery({
    queryKey: ["parking-objects"],
    queryFn: getAllObjects,
    refetchInterval: pollMs,
    refetchOnWindowFocus: true,
  });

  const data = query.data ? shapeParkingData(query.data, selectedLotId) : undefined;

  return {
    ...query,
    data,
    lastUpdated: query.dataUpdatedAt ? new Date(query.dataUpdatedAt) : null,
  };
}

// Prediction for a given lot + timestamp (used by PredictionsView)
export function useLotPrediction(lotId: string | null, targetTimestamp: string | null) {
  return useQuery({
    queryKey: ["prediction", lotId, targetTimestamp],
    queryFn: () => predictAvailability(lotId!, targetTimestamp!),
    enabled: Boolean(lotId && targetTimestamp),
    staleTime: 60_000,
    retry: false,
  });
}

export function useLotPredictionSeries(lotId: string | null, targetTimestamps: string[]) {
  return useQueries({
    queries: targetTimestamps.map((targetTimestamp) => ({
      queryKey: ["prediction", lotId, targetTimestamp],
      queryFn: () => predictAvailability(lotId!, targetTimestamp),
      enabled: Boolean(lotId && targetTimestamp),
      staleTime: 60_000,
      retry: false,
    })),
  });
}

// ── Full-day forecast with confidence band (XGBoost quantile model) ──
export interface ForecastPoint {
  hour: number;
  occupancyPct: number;
  freeMedian: number;
  freeLower: number;
  freeUpper: number;
}
export interface DayForecast {
  date: string;
  totalSlots: number;
  isHoliday: boolean;
  holidayName: string | null;
  points: ForecastPoint[];
}

// Raw objects (shares the cache/queryKey with useParkingData), polled.
export function useAllObjects(pollMs = 5000) {
  return useQuery({ queryKey: ["parking-objects"], queryFn: getAllObjects, refetchInterval: pollMs });
}

// ── Admin data (users + command history), polled ──
export function useAdminUsers(pollMs = 8000) {
  return useQuery({ queryKey: ["admin-users"], queryFn: getAllUsers, refetchInterval: pollMs });
}

export function useAdminCommands(pollMs = 5000) {
  return useQuery({ queryKey: ["admin-commands"], queryFn: getAllCommands, refetchInterval: pollMs });
}

// Calls the ML service's /forecast (via the Vite "/ml" proxy) for one date.
export function useDayForecast(dateYmd: string | null, totalSlots: number) {
  return useQuery({
    queryKey: ["forecast", dateYmd, totalSlots],
    queryFn: async (): Promise<DayForecast> => {
      const res = await fetch(mlUrl("/forecast"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ date: dateYmd, totalSlots }),
      });
      if (!res.ok) throw new Error("forecast " + res.status);
      return res.json();
    },
    enabled: Boolean(dateYmd && totalSlots > 0),
    staleTime: 60_000,
    retry: false,
  });
}
