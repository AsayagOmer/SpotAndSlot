import { useState } from "react";
import {
  Plus, Trash2, Pencil, Accessibility, MapPin, Loader2, ChevronDown, ChevronUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  ObjectType, createObject, updateObject, deleteObject, type ObjectBoundary,
} from "@/lib/api";
import { det, type Act } from "../hooks";
import { RenameDialog, DeleteButton } from "./shared";

// ── Create-lot dialog ─────────────────────────────────────────────────────────

export const CreateLotDialog = ({ onCreate }: { onCreate: (lot: ObjectBoundary) => Promise<unknown> }) => {
  const [open, setOpen] = useState(false);
  const [alias, setAlias] = useState("");
  const [city, setCity] = useState("");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      const lot: ObjectBoundary = {
        type: ObjectType.PARKING_LOT,
        alias: alias.trim(),
        status: "OPEN", // status is required by the server
        active: true,
        objectDetails: city.trim() ? { address: city.trim() } : {},
      };
      const latN = parseFloat(lat), lngN = parseFloat(lng);
      if (!Number.isNaN(latN) && !Number.isNaN(lngN)) lot.location = { lat: latN, lng: lngN };
      await onCreate(lot);
      setOpen(false);
      setAlias(""); setCity(""); setLat(""); setLng("");
    } catch { /* toast shown by act() */ }
    finally { setBusy(false); }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm"><Plus className="w-4 h-4 mr-1.5" /> New lot</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>Create a parking lot</DialogTitle></DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="lot-name">Name</Label>
            <Input id="lot-name" placeholder="e.g. Dizengoff Center Lot" value={alias}
              onChange={(e) => setAlias(e.target.value)} autoFocus />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="lot-city">City / address</Label>
            <Input id="lot-city" placeholder="e.g. Tel Aviv" value={city}
              onChange={(e) => setCity(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="lot-lat">Latitude (optional)</Label>
              <Input id="lot-lat" placeholder="32.08" value={lat} onChange={(e) => setLat(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="lot-lng">Longitude (optional)</Label>
              <Input id="lot-lng" placeholder="34.78" value={lng} onChange={(e) => setLng(e.target.value)} />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button onClick={submit} disabled={busy || !alias.trim()}>
            {busy && <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />} Create lot
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

// ── Section & slot management (inside a lot card) ─────────────────────────────

export const ManageSections = ({
  lotId, sections, slots, act,
}: {
  lotId: string;
  sections: ObjectBoundary[];
  slots: ObjectBoundary[];
  act: Act;
}) => {
  const [openManage, setOpenManage] = useState(false);
  const [newSection, setNewSection] = useState("");

  const addSection = () =>
    act("Section added", () =>
      createObject({
        type: ObjectType.PARKING_SECTION,
        alias: newSection.trim(),
        status: "OPEN", // status is required by the server
        active: true,
        objectDetails: { parentLotId: lotId },
      }),
    ).then(() => setNewSection(""));

  return (
    <div className="mt-4 border-t border-border pt-3">
      <button
        onClick={() => setOpenManage((o) => !o)}
        className="flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
      >
        {openManage ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        Manage sections & slots
      </button>

      {openManage && (
        <div className="mt-3 space-y-4">
          {sections
            .slice()
            .sort((a, b) => (a.alias ?? "").localeCompare(b.alias ?? "", undefined, { numeric: true }))
            .map((sec) => {
              const secId = sec.id?.objectId ?? "";
              const secSlots = slots
                .filter((s) => det(s).parentSectionId === secId)
                .sort((a, b) => (a.alias ?? "").localeCompare(b.alias ?? "", undefined, { numeric: true }));
              return (
                <SectionEditor
                  key={secId}
                  lotId={lotId}
                  section={sec}
                  slots={secSlots}
                  act={act}
                />
              );
            })}
          {sections.length === 0 && (
            <p className="text-sm text-muted-foreground">No sections yet — add the first one.</p>
          )}

          {/* add section */}
          <div className="flex items-center gap-2">
            <Input
              placeholder="New section name (e.g. Section D)"
              value={newSection}
              onChange={(e) => setNewSection(e.target.value)}
              className="max-w-xs h-9"
            />
            <Button size="sm" variant="outline" disabled={!newSection.trim()} onClick={addSection}>
              <Plus className="w-4 h-4 mr-1" /> Add section
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

const SectionEditor = ({
  lotId, section, slots, act,
}: {
  lotId: string;
  section: ObjectBoundary;
  slots: ObjectBoundary[];
  act: Act;
}) => {
  const secId = section.id?.objectId ?? "";
  const [newSlot, setNewSlot] = useState("");
  const [newKind, setNewKind] = useState<"REGULAR" | "HANDICAP">("REGULAR");

  const addSlot = () =>
    act("Slot added", () =>
      createObject({
        type: ObjectType.PARKING_SLOT,
        alias: newSlot.trim(),
        status: "FREE",
        active: true,
        objectDetails: {
          parentLotId: lotId,
          parentSectionId: secId,
          slotKind: newKind,
          healthStatus: "OK",
        },
      }),
    ).then(() => setNewSlot(""));

  return (
    <div className="rounded-xl bg-muted/40 p-3 space-y-2.5">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 font-medium text-sm">
          <MapPin className="w-3.5 h-3.5 text-primary" />
          {section.alias}
          <span className="text-xs text-muted-foreground font-normal">({slots.length} slots)</span>
          <RenameDialog
            title="Rename section"
            current={section.alias ?? ""}
            onSave={(name) => act("Section renamed", () => updateObject(secId, { alias: name }))}
          >
            <button className="w-6 h-6 rounded flex items-center justify-center text-muted-foreground hover:text-primary hover:bg-muted transition-colors">
              <Pencil className="w-3 h-3" />
            </button>
          </RenameDialog>
        </div>
        <DeleteButton
          what={`section "${section.alias}"`}
          size="icon-xs"
          onDelete={() => act("Section deleted", () => deleteObject(secId))}
        />
      </div>

      {/* slot chips */}
      <div className="flex flex-wrap gap-1.5">
        {slots.map((slot) => (
          <SlotChip key={slot.id?.objectId} slot={slot} act={act} />
        ))}
        {slots.length === 0 && <span className="text-xs text-muted-foreground">No slots.</span>}
      </div>

      {/* add slot */}
      <div className="flex items-center gap-2 flex-wrap">
        <Input
          placeholder="Slot name (e.g. A-12)"
          value={newSlot}
          onChange={(e) => setNewSlot(e.target.value)}
          className="max-w-[150px] h-8 text-sm"
        />
        <Select value={newKind} onValueChange={(v) => setNewKind(v as "REGULAR" | "HANDICAP")}>
          <SelectTrigger className="w-[130px] h-8 text-sm"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="REGULAR">Regular</SelectItem>
            <SelectItem value="HANDICAP">Handicap ♿</SelectItem>
          </SelectContent>
        </Select>
        <Button size="sm" variant="outline" className="h-8" disabled={!newSlot.trim()} onClick={addSlot}>
          <Plus className="w-3.5 h-3.5 mr-1" /> Add slot
        </Button>
      </div>
    </div>
  );
};

const SlotChip = ({ slot, act }: { slot: ObjectBoundary; act: Act }) => {
  const d = det(slot);
  const slotId = slot.id?.objectId ?? "";
  const occupied = slot.status === "OCCUPIED";
  const handicap = d.slotKind === "HANDICAP";
  const faulty = d.healthStatus === "FAULTY";

  const toggleStatus = () =>
    act(`Slot ${slot.alias} → ${occupied ? "FREE" : "OCCUPIED"}`, () =>
      updateObject(slotId, { status: occupied ? "FREE" : "OCCUPIED" }));

  // objectDetails is replaced wholesale server-side, so send the full map back
  const toggleKind = () =>
    act(`Slot ${slot.alias} → ${handicap ? "REGULAR" : "HANDICAP"}`, () =>
      updateObject(slotId, {
        objectDetails: { ...(slot.objectDetails ?? {}), slotKind: handicap ? "REGULAR" : "HANDICAP" },
      }));

  const fixHealth = () =>
    act(`Slot ${slot.alias} marked healthy`, () =>
      updateObject(slotId, {
        objectDetails: { ...(slot.objectDetails ?? {}), healthStatus: "OK" },
      }));

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          title={`${slot.alias} · ${slot.status}${handicap ? " · handicap" : ""}${faulty ? " · FAULTY" : ""}`}
          className={`inline-flex items-center gap-1 px-2 py-1 rounded-md border text-xs font-medium transition-colors ${
            faulty
              ? "border-destructive/50 bg-destructive/10 text-destructive"
              : occupied
              ? "border-border bg-muted text-muted-foreground"
              : "border-emerald-500/40 bg-emerald-500/10 text-emerald-700"
          }`}
        >
          {handicap && <Accessibility className="w-3 h-3" />}
          {slot.alias}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuItem onClick={toggleStatus}>
          Mark {occupied ? "FREE" : "OCCUPIED"}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={toggleKind}>
          Make {handicap ? "regular" : "handicap ♿"}
        </DropdownMenuItem>
        {faulty && (
          <DropdownMenuItem onClick={fixHealth}>Mark healthy (OK)</DropdownMenuItem>
        )}
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onClick={() => act(`Slot ${slot.alias} deleted`, () => deleteObject(slotId))}
        >
          <Trash2 className="w-3.5 h-3.5 mr-1.5" /> Delete slot
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
