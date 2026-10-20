import { useMemo } from "react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { useAllObjects } from "@/hooks/useParkingData";
import { ObjectType, type ObjectBoundary, type SlotDetails } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export const det = (o: ObjectBoundary) => (o.objectDetails ?? {}) as SlotDetails;

export interface LotSummary {
  lot: ObjectBoundary;
  lotId?: string;
  alias: string;
  sections: ObjectBoundary[];
  slots: ObjectBoundary[];
  occupied: number;
  free: number;
  faulty: number;
  occupancy: number;
}

export interface AdminStats {
  lots: ObjectBoundary[];
  sections: ObjectBoundary[];
  slots: ObjectBoundary[];
  vehicles: ObjectBoundary[];
  occupied: number;
  free: number;
  faulty: ObjectBoundary[];
  occupancy: number;
  perLot: LotSummary[];
  totalLots: number;
}

// Objects + derived stats, scoped to the lots the signed-in admin owns.
export function useAdminStats() {
  const { user } = useAuth();
  const objectsQ = useAllObjects();
  const objects = useMemo(() => objectsQ.data ?? [], [objectsQ.data]);
  const myEmail = user?.email ?? "";

  const stats = useMemo<AdminStats>(() => {
    const allLots = objects.filter((o) => o.type === ObjectType.PARKING_LOT);
    const lots = allLots.filter((l) => l.createdBy?.userId.email === myEmail);
    const lotIds = new Set(lots.map((l) => l.id?.objectId));
    const sections = objects.filter(
      (o) => o.type === ObjectType.PARKING_SECTION
        && lotIds.has((o.objectDetails as { parentLotId?: string } | undefined)?.parentLotId),
    );
    const slots = objects.filter(
      (o) => o.type === ObjectType.PARKING_SLOT && lotIds.has(det(o).parentLotId),
    );
    const vehicles = objects.filter(
      (o) => o.type === ObjectType.VEHICLE
        && lotIds.has((o.objectDetails as { lotId?: string } | undefined)?.lotId),
    );
    const occupied = slots.filter((s) => s.status === "OCCUPIED").length;
    const faulty = slots.filter((s) => det(s).healthStatus === "FAULTY");
    const free = slots.length - occupied;
    const occupancy = slots.length ? Math.round((occupied / slots.length) * 100) : 0;

    const perLot: LotSummary[] = lots.map((lot) => {
      const lotId = lot.id?.objectId;
      const lotSlots = slots.filter((s) => det(s).parentLotId === lotId);
      const lotSections = sections.filter(
        (sec) => (sec.objectDetails as { parentLotId?: string })?.parentLotId === lotId,
      );
      const lotOcc = lotSlots.filter((s) => s.status === "OCCUPIED").length;
      return {
        lot, lotId, alias: lot.alias ?? "Lot",
        sections: lotSections,
        slots: lotSlots,
        occupied: lotOcc,
        free: lotSlots.length - lotOcc,
        faulty: lotSlots.filter((s) => det(s).healthStatus === "FAULTY").length,
        occupancy: lotSlots.length ? Math.round((lotOcc / lotSlots.length) * 100) : 0,
      };
    });

    return { lots, sections, slots, vehicles, occupied, free, faulty, occupancy, perLot, totalLots: allLots.length };
  }, [objects, myEmail]);

  return { objectsQ, objects, stats, myEmail };
}

// objectId -> {type, alias} for resolving command targets in the activity feed
export function useObjectMap(objects: ObjectBoundary[]) {
  return useMemo(() => {
    const m = new Map<string, { type: string; alias?: string }>();
    objects.forEach((o) => { if (o.id?.objectId) m.set(o.id.objectId, { type: o.type, alias: o.alias }); });
    return m;
  }, [objects]);
}

// Wraps a mutation with a toast + parking-objects cache refresh.
export function useAct() {
  const queryClient = useQueryClient();
  return async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn();
      queryClient.invalidateQueries({ queryKey: ["parking-objects"] });
      toast.success(label);
    } catch (e) {
      toast.error(`${label} failed: ${(e as Error).message}`);
      throw e;
    }
  };
}

export type Act = ReturnType<typeof useAct>;
