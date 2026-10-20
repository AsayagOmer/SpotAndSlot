import { useState, useEffect } from "react";
import { Loader2, WifiOff, X, Accessibility } from "lucide-react";
import ParkingSpot from "./ParkingSpot";
import { useParkingData } from "@/hooks/useParkingData";

const ParkingLotMap = () => {
  // Live data polled from the Smart Parking API
  const { data, isLoading, isError, lastUpdated, refetch, isFetching } = useParkingData(5000);

  const [timeAgoText, setTimeAgoText] = useState<string>("עכשיו");

  // "time ago" text reflects the real last successful fetch
  useEffect(() => {
    const update = () => {
      if (!lastUpdated) {
        setTimeAgoText("—");
        return;
      }
      const diff = Math.floor((Date.now() - lastUpdated.getTime()) / 1000);
      if (diff < 5) setTimeAgoText("עכשיו");
      else if (diff < 60) setTimeAgoText(`לפני ${diff} שניות`);
      else setTimeAgoText(`לפני ${Math.floor(diff / 60)} דקות`);
    };
    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [lastUpdated]);

  // ── Loading state ──
  if (isLoading) {
    return (
      <div className="card-elevated p-10 flex flex-col items-center justify-center gap-3 animate-fade-in">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
        <p className="text-muted-foreground">טוען נתוני חניון…</p>
      </div>
    );
  }

  // ── Error / server offline state ──
  if (isError || !data || data.totalSpots === 0) {
    return (
      <div className="card-elevated p-10 flex flex-col items-center justify-center gap-3 animate-fade-in text-center">
        <WifiOff className="w-8 h-8 text-destructive" />
        <p className="font-semibold">אין חיבור לשרת החניות</p>
        <p className="text-sm text-muted-foreground">
          ודא שה-API פועל בכתובת<br />
          <code className="text-xs">{import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8084/ambient-invisible-intelligence"}</code>
        </p>
        <button
          onClick={() => refetch()}
          className="mt-2 bg-primary text-primary-foreground rounded-xl px-4 py-2 text-sm font-medium hover:opacity-90 transition-opacity"
        >
          נסה שוב
        </button>
      </div>
    );
  }

  const { sections, occupancyRate, lotAlias } = data;

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="card-elevated p-4 relative overflow-hidden">
        {/* Scanning Line Effect */}
        <div className="absolute inset-0 pointer-events-none z-0">
          <div className="w-full h-[5px] bg-primary/20 blur-sm animate-scan-line" />
        </div>

        {/* Header */}
        <div className="flex items-center justify-between mb-4 relative z-10">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-lg">{lotAlias ?? "חניון"}</span>
            <div className="flex items-center gap-1.5 bg-muted/50 px-3 py-1.5 rounded-full">
              <div className="relative flex h-2 w-2">
                <span
                  className={`animate-pulse-ring absolute inline-flex h-full w-full rounded-full opacity-75 ${
                    isFetching ? "bg-primary" : "bg-destructive"
                  }`}
                ></span>
                <span
                  className={`relative inline-flex rounded-full h-2 w-2 ${
                    isFetching ? "bg-primary" : "bg-destructive"
                  }`}
                ></span>
              </div>
              <span className="text-[10px] text-muted-foreground">עודכן: {timeAgoText}</span>
            </div>
          </div>
          <span className="bg-primary/10 text-primary text-base font-medium px-4 py-2 rounded-full transition-all duration-300">
            {occupancyRate}% תפוס
          </span>
        </div>

        {/* Parking Grid - one bordered block per section, driven by live data */}
        <div className="flex flex-col items-center gap-3 py-4 bg-muted/30 rounded-xl p-3 relative z-10 transition-colors duration-500">
          {sections.map((section) => (
            <div key={section.sectionId} className="w-full flex flex-col items-center gap-1">
              <span className="text-[11px] text-muted-foreground font-medium self-end pr-1">
                {section.alias}
              </span>
              <div className="flex flex-wrap gap-1 justify-center border-2 border-foreground/30 p-1.5 rounded">
                {section.spots.map((spot) => (
                  <ParkingSpot key={spot.objectId || spot.id} id={spot.id} status={spot.status} size="sm" />
                ))}
              </div>
            </div>
          ))}
        </div>

        {/* Legend */}
        <div className="flex flex-wrap items-center justify-center gap-4 mt-6 text-sm relative z-10">
          <div className="flex items-center gap-1.5">
            <span className="w-4 h-4 rounded-sm bg-primary border border-foreground/20" />
            <span>פנוי</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-4 h-4 rounded-sm bg-gray-500 border border-gray-600 flex items-center justify-center">
              <X className="w-3 h-3 text-white" />
            </span>
            <span>תפוס</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-4 h-4 rounded-sm bg-primary border border-foreground/20 flex items-center justify-center">
              <Accessibility className="w-3 h-3 text-primary-foreground" />
            </span>
            <span>נכים פנוי</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-4 h-4 rounded-sm bg-gray-500 border border-gray-600 flex items-center justify-center">
              <Accessibility className="w-3 h-3 text-white" />
            </span>
            <span>נכים תפוס</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ParkingLotMap;
