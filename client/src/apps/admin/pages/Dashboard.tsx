import { Link } from "react-router-dom";
import {
  LayoutGrid, MapPin, Database, CheckCircle2, Car, AlertTriangle, ArrowRight,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { apiConfig } from "@/lib/api";
import { useAdminStats } from "../hooks";
import { StatCard } from "../components/shared";

// System overview for the signed-in admin's lots.
const Dashboard = () => {
  const { objectsQ, stats } = useAdminStats();
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
      {/* Stat cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3">
        {loading ? (
          Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-[72px] rounded-xl" />)
        ) : (
          <>
            <StatCard icon={LayoutGrid} label="My lots" value={stats.lots.length}
              sub={`of ${stats.totalLots} in system`} />
            <StatCard icon={MapPin} label="Sections" value={stats.sections.length} />
            <StatCard icon={Database} label="Slots" value={stats.slots.length}
              sub={`${stats.occupancy}% full`} tone="primary" />
            <StatCard icon={CheckCircle2} label="Free now" value={stats.free} tone="ok" />
            <StatCard icon={Car} label="Vehicle events" value={stats.vehicles.length} />
            <StatCard icon={AlertTriangle} label="Faulty slots" value={stats.faulty.length}
              tone={stats.faulty.length ? "danger" : "ok"} />
          </>
        )}
      </div>

      {/* Per-lot occupancy overview */}
      <div className="grid md:grid-cols-2 xl:grid-cols-3 gap-4">
        {stats.perLot.map((l) => (
          <Card key={l.lotId} className="p-5">
            <div className="flex items-start justify-between gap-2">
              <h3 className="font-semibold flex items-center gap-2">
                <LayoutGrid className="w-4 h-4 text-primary" /> {l.alias}
              </h3>
              {l.faulty > 0 && (
                <Badge variant="outline" className="border-destructive/40 text-destructive">
                  {l.faulty} faulty
                </Badge>
              )}
            </div>
            <div className="flex items-baseline gap-2 mt-3">
              <span className="text-3xl font-bold tabular-nums text-primary">{l.free}</span>
              <span className="text-sm text-muted-foreground">free of {l.slots.length} slots</span>
            </div>
            <div className="mt-3">
              <div className="flex justify-between text-xs text-muted-foreground mb-1">
                <span>Occupancy</span><span>{l.occupancy}%</span>
              </div>
              <div className="h-2 rounded-full bg-muted overflow-hidden">
                <div className="h-full bg-primary rounded-full transition-all" style={{ width: `${l.occupancy}%` }} />
              </div>
            </div>
            <Link
              to="/lots"
              className="mt-4 inline-flex items-center gap-1 text-sm text-primary hover:underline"
            >
              Manage <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </Card>
        ))}
        {stats.perLot.length === 0 && !loading && (
          <Card className="p-8 text-center text-muted-foreground md:col-span-2 xl:col-span-3">
            You don't manage any parking lots yet — create your first one in{" "}
            <Link to="/lots" className="text-primary hover:underline">My Lots</Link>.
          </Card>
        )}
      </div>
    </>
  );
};

export default Dashboard;
