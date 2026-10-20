import {
  LayoutGrid, Pencil,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import {
  apiConfig, createObject, updateObject, deleteObject,
} from "@/lib/api";
import { useAdminStats, useAct } from "../hooks";
import { Metric, RenameDialog, DeleteButton } from "../components/shared";
import { CreateLotDialog, ManageSections } from "../components/lots";

// Full CRUD over the lots this OPERATOR owns: create/rename/delete lots and
// manage their sections & slots. (Health checks are commands, which only
// END_USERs may invoke, so they are not offered here.)
const Lots = () => {
  const { objectsQ, stats } = useAdminStats();
  const act = useAct();
  const loading = objectsQ.isLoading;

  if (objectsQ.isError) {
    return (
      <Card className="p-8 text-center text-destructive">
        Could not reach the API at <code>{apiConfig.BASE_URL}</code>. Is the server running?
      </Card>
    );
  }

  return (
    <>
      <div className="flex justify-end">
        <CreateLotDialog onCreate={(input) => act("Lot created", () => createObject(input))} />
      </div>

      {stats.perLot.length === 0 && !loading && (
        <Card className="p-8 text-center text-muted-foreground">
          You don't manage any parking lots yet — create your first one.
        </Card>
      )}

      {stats.perLot.map((l) => (
        <Card key={l.lotId} className="p-5">
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div>
              <h3 className="font-semibold text-lg flex items-center gap-2">
                <LayoutGrid className="w-4 h-4 text-primary" /> {l.alias}
                <RenameDialog
                  title="Rename lot"
                  current={l.alias}
                  onSave={(name) => act("Lot renamed", () => updateObject(l.lotId!, { alias: name }))}
                >
                  <button className="w-6 h-6 rounded flex items-center justify-center text-muted-foreground hover:text-primary hover:bg-muted transition-colors">
                    <Pencil className="w-3.5 h-3.5" />
                  </button>
                </RenameDialog>
              </h3>
              <p className="text-xs text-muted-foreground mt-0.5">id: {l.lotId}</p>
            </div>
            <DeleteButton
              what={`lot "${l.alias}"`}
              onDelete={() => act("Lot deleted", () => deleteObject(l.lotId!))}
            />
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mt-4">
            <Metric label="Sections" value={l.sections.length} />
            <Metric label="Slots" value={l.slots.length} />
            <Metric label="Free" value={l.free} tone="ok" />
            <Metric label="Occupied" value={l.occupied} />
            <Metric label="Faulty" value={l.faulty} tone={l.faulty ? "danger" : "ok"} />
          </div>

          {/* occupancy bar */}
          <div className="mt-4">
            <div className="flex justify-between text-xs text-muted-foreground mb-1">
              <span>Occupancy</span><span>{l.occupancy}%</span>
            </div>
            <div className="h-2 rounded-full bg-muted overflow-hidden">
              <div className="h-full bg-primary rounded-full transition-all"
                style={{ width: `${l.occupancy}%` }} />
            </div>
          </div>

          {/* section & slot management */}
          <ManageSections
            lotId={l.lotId!}
            sections={l.sections}
            slots={l.slots}
            act={act}
          />
        </Card>
      ))}
    </>
  );
};

export default Lots;
