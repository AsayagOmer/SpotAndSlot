import { describe, expect, it } from "vitest";
import { shapeParkingData } from "@/hooks/useParkingData";
import type { ObjectBoundary } from "@/lib/api";

// ── fixture builders ──────────────────────────────────────────────────────────

const lot = (id: string, alias: string): ObjectBoundary => ({
  id: { objectId: id, systemID: "sys" },
  type: "PARKING_LOT",
  alias,
  active: true,
});

const section = (id: string, alias: string, lotId: string): ObjectBoundary => ({
  id: { objectId: id, systemID: "sys" },
  type: "PARKING_SECTION",
  alias,
  active: true,
  objectDetails: { parentLotId: lotId },
});

const slot = (
  id: string,
  alias: string,
  lotId: string,
  sectionId: string,
  status: "FREE" | "OCCUPIED",
  kind: "REGULAR" | "HANDICAP" = "REGULAR",
  active = true,
): ObjectBoundary => ({
  id: { objectId: id, systemID: "sys" },
  type: "PARKING_SLOT",
  alias,
  status,
  active,
  objectDetails: { parentLotId: lotId, parentSectionId: sectionId, slotKind: kind },
});

const fixture: ObjectBoundary[] = [
  lot("lot-1", "North Lot"),
  lot("lot-2", "South Lot"),
  section("sec-1", "Section A", "lot-1"),
  section("sec-2", "Section B", "lot-2"),
  slot("s1", "A-1", "lot-1", "sec-1", "FREE"),
  slot("s2", "A-2", "lot-1", "sec-1", "OCCUPIED"),
  slot("s3", "A-10", "lot-1", "sec-1", "FREE", "HANDICAP"),
  slot("s4", "B-1", "lot-2", "sec-2", "OCCUPIED"),
];

// ── tests ─────────────────────────────────────────────────────────────────────

describe("shapeParkingData", () => {
  it("shapes the selected lot only", () => {
    const data = shapeParkingData(fixture, "lot-2");
    expect(data.lotId).toBe("lot-2");
    expect(data.lotAlias).toBe("South Lot");
    expect(data.totalSpots).toBe(1);
    expect(data.occupiedCount).toBe(1);
    expect(data.freeCount).toBe(0);
    expect(data.occupancyRate).toBe(100);
  });

  it("falls back to the first lot when nothing is selected", () => {
    const data = shapeParkingData(fixture, null);
    expect(data.lotId).toBe("lot-1");
    expect(data.totalSpots).toBe(3);
  });

  it("falls back to the first lot when the selection no longer exists", () => {
    const data = shapeParkingData(fixture, "deleted-lot");
    expect(data.lotId).toBe("lot-1");
  });

  it("maps slot kind and status to GUI spot statuses", () => {
    const spots = shapeParkingData(fixture, "lot-1").sections[0].spots;
    const byId = Object.fromEntries(spots.map((s) => [s.id, s.status]));
    expect(byId["A-1"]).toBe("available");
    expect(byId["A-2"]).toBe("occupied");
    expect(byId["A-10"]).toBe("disabled-available");
  });

  it("sorts spots naturally (A-2 before A-10)", () => {
    const spots = shapeParkingData(fixture, "lot-1").sections[0].spots;
    expect(spots.map((s) => s.id)).toEqual(["A-1", "A-2", "A-10"]);
  });

  it("ignores inactive slots", () => {
    const withInactive = [...fixture, slot("s5", "A-3", "lot-1", "sec-1", "FREE", "REGULAR", false)];
    const data = shapeParkingData(withInactive, "lot-1");
    expect(data.totalSpots).toBe(3);
    expect(data.sections[0].spots.map((s) => s.id)).not.toContain("A-3");
  });

  it("computes the occupancy rate as a rounded percentage", () => {
    const data = shapeParkingData(fixture, "lot-1");
    // 1 occupied of 3 -> 33%
    expect(data.occupancyRate).toBe(33);
  });

  it("returns an empty shape when there are no lots", () => {
    const data = shapeParkingData([], null);
    expect(data.lotId).toBeNull();
    expect(data.sections).toEqual([]);
    expect(data.totalSpots).toBe(0);
    expect(data.occupancyRate).toBe(0);
  });
});
