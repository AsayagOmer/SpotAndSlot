import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { getAllObjects, ObjectType, type ObjectBoundary } from "@/lib/api";

// ─────────────────────────────────────────────────────────────────────────────
// Multi-lot support: which parking lot the user is currently viewing.
// The choice is persisted and defaults to the first lot returned by the API.
// ─────────────────────────────────────────────────────────────────────────────

const STORAGE_KEY = "spot-insight.selected-lot";

interface SelectedLotContextValue {
  lots: ObjectBoundary[];
  selectedLotId: string | null;
  setSelectedLotId: (lotId: string) => void;
}

const SelectedLotContext = createContext<SelectedLotContextValue | null>(null);

export function SelectedLotProvider({ children }: { children: ReactNode }) {
  // shares the ["parking-objects"] cache with useParkingData
  const { data: objects } = useQuery({
    queryKey: ["parking-objects"],
    queryFn: getAllObjects,
    refetchInterval: 10000,
  });

  const lots = useMemo(
    () => (objects ?? []).filter((o) => o.type === ObjectType.PARKING_LOT && o.active !== false),
    [objects],
  );

  const [selectedLotId, setSelectedLotIdState] = useState<string | null>(
    () => localStorage.getItem(STORAGE_KEY),
  );

  // fall back to the first lot when nothing is selected (or the lot was deleted)
  useEffect(() => {
    if (lots.length === 0) return;
    if (!selectedLotId || !lots.some((l) => l.id?.objectId === selectedLotId)) {
      setSelectedLotIdState(lots[0].id?.objectId ?? null);
    }
  }, [lots, selectedLotId]);

  const value = useMemo<SelectedLotContextValue>(
    () => ({
      lots,
      selectedLotId,
      setSelectedLotId: (lotId: string) => {
        setSelectedLotIdState(lotId);
        localStorage.setItem(STORAGE_KEY, lotId);
      },
    }),
    [lots, selectedLotId],
  );

  return <SelectedLotContext.Provider value={value}>{children}</SelectedLotContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSelectedLot(): SelectedLotContextValue {
  const ctx = useContext(SelectedLotContext);
  if (!ctx) throw new Error("useSelectedLot must be used within a SelectedLotProvider");
  return ctx;
}
