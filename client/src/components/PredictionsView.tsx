import { useEffect, useMemo, useState } from "react";
import { format, addDays } from "date-fns";
import { Calendar, Clock, Sparkles, CloudSun, Server, Loader2, PartyPopper } from "lucide-react";
import { AreaChart, Area, XAxis, YAxis, ResponsiveContainer, ReferenceDot, Tooltip } from "recharts";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar as CalendarComponent } from "@/components/ui/calendar";
import { cn } from "@/lib/utils";
import SmartInsights from "@/components/SmartInsights";
import { useParkingData, useDayForecast } from "@/hooks/useParkingData";

const timeOptions = [
  "06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
  "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00",
];

const HOUR_MIN = 6;
const HOUR_MAX = 22;

const parseHour = (t: string) => parseInt(t.slice(0, 2), 10);
const fmtHour = (h: number) => `${h < 10 ? "0" : ""}${Math.round(h)}:00`;

const PredictionsView = () => {
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [selectedTime, setSelectedTime] = useState<string>("09:00");
  const [dateOpen, setDateOpen] = useState(false);
  const [timeOpen, setTimeOpen] = useState(false);

  const targetHour = parseHour(selectedTime);

  // smooth animated marker
  const [animatedHour, setAnimatedHour] = useState(targetHour);
  useEffect(() => {
    if (Math.abs(animatedHour - targetHour) < 0.01) {
      if (animatedHour !== targetHour) setAnimatedHour(targetHour);
      return;
    }
    let raf: number;
    const step = () => {
      setAnimatedHour((p) => (Math.abs(targetHour - p) < 0.01 ? targetHour : p + (targetHour - p) * 0.18));
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [targetHour, animatedHour]);

  // lot context (capacity) from the live API
  const { data: parking } = useParkingData(10000);
  const totalSpots = parking?.totalSpots ?? 0;
  const lotAlias = parking?.lotAlias ?? "החניון";

  // real model forecast for the chosen day (one call, with P10–P90 band)
  const dateYmd = format(selectedDate, "yyyy-MM-dd");
  const { data: forecast, isFetching } = useDayForecast(totalSpots > 0 ? dateYmd : null, totalSpots);

  // build chart series: occupancy median + band (in occupancy %), within operating hours
  const chartData = useMemo(() => {
    if (!forecast || totalSpots <= 0) return [];
    return forecast.points
      .filter((p) => p.hour >= HOUR_MIN && p.hour <= HOUR_MAX)
      .map((p) => {
        const occLow = Math.round(((totalSpots - p.freeUpper) / totalSpots) * 100); // optimistic
        const occHigh = Math.round(((totalSpots - p.freeLower) / totalSpots) * 100); // pessimistic
        return {
          hour: p.hour,
          occ: p.occupancyPct,
          band: [Math.max(0, Math.min(occLow, occHigh)), Math.min(100, Math.max(occLow, occHigh))] as [number, number],
          free: p.freeMedian,
        };
      });
  }, [forecast, totalSpots]);

  const pointAt = (h: number) => chartData.reduce(
    (best, p) => (Math.abs(p.hour - h) < Math.abs(best.hour - h) ? p : best),
    chartData[0] ?? { hour: targetHour, occ: 0, band: [0, 0] as [number, number], free: 0 },
  );
  const selected = chartData.length ? pointAt(targetHour) : null;
  const currentOccupancy = selected?.occ ?? 0;
  const predictedFree = selected?.free ?? 0;

  // best (least busy) hour to arrive
  const bestPoint = useMemo(
    () => (chartData.length ? chartData.reduce((b, p) => (p.occ < b.occ ? p : b)) : null),
    [chartData],
  );

  const trendDelta = useMemo(() => {
    if (!chartData.length) return 0;
    const morning = pointAt(8).occ;
    const noon = pointAt(13).occ;
    return Math.round(noon - morning);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartData]);

  const isToday = format(selectedDate, "yyyy-MM-dd") === format(new Date(), "yyyy-MM-dd");
  const dateDisplayText = isToday ? "היום" : format(selectedDate, "dd/MM/yyyy");

  const velocity = Math.abs(targetHour - animatedHour);
  const blurAmount = Math.min(4, velocity * 20);

  return (
    <div className="space-y-5 animate-fade-in">
      {/* Title */}
      <div className="text-right">
        <h2 className="text-2xl font-bold">תחזיות תפוסה</h2>
        <p className="text-muted-foreground mt-1">תכנן את ההגעה שלך ל{lotAlias}</p>
      </div>



      {/* Date/Time selectors */}
      <div className="flex gap-3">
        <Popover open={timeOpen} onOpenChange={setTimeOpen}>
          <PopoverTrigger asChild>
            <button className="flex-1 flex items-center justify-center gap-2 bg-card rounded-2xl py-3 px-4 border border-border hover:border-primary/30 transition-colors">
              <span className="text-base font-medium">{selectedTime}</span>
              <Clock className="w-4 h-4 text-primary" />
            </button>
          </PopoverTrigger>
          <PopoverContent className="w-48 p-2 max-h-64 overflow-y-auto" align="start">
            <div className="flex flex-col gap-1">
              {timeOptions.map((time) => (
                <button
                  key={time}
                  onClick={() => { setSelectedTime(time); setTimeOpen(false); }}
                  className={cn(
                    "text-base py-2 px-3 rounded-lg text-right transition-colors hover:bg-muted",
                    selectedTime === time ? "bg-primary text-primary-foreground" : "",
                  )}
                >
                  {time}
                </button>
              ))}
            </div>
          </PopoverContent>
        </Popover>

        <Popover open={dateOpen} onOpenChange={setDateOpen}>
          <PopoverTrigger asChild>
            <button className="flex-1 flex items-center justify-center gap-2 bg-card rounded-2xl py-3 px-4 border border-border hover:border-primary/30 transition-colors">
              <span className="text-base font-medium">{dateDisplayText}</span>
              <Calendar className="w-4 h-4 text-primary" />
            </button>
          </PopoverTrigger>
          <PopoverContent className="w-auto p-0" align="end">
            <CalendarComponent
              mode="single"
              selected={selectedDate}
              onSelect={(date) => { if (date) { setSelectedDate(date); setDateOpen(false); } }}
              initialFocus
              className={cn("p-3 pointer-events-auto")}
            />
          </PopoverContent>
        </Popover>
      </div>

      {/* Main forecast card */}
      <div className="card-elevated p-5 transition-all duration-300">
        <div className="flex items-start justify-between mb-2">
          <div className="text-right">
            <h3 className="font-bold text-lg">תפוסה צפויה</h3>
            <p className="text-sm text-muted-foreground mt-1">
              {predictedFree} מקומות פנויים צפויים מתוך {totalSpots}
            </p>
          </div>
          <div className="text-left">
            <span className="text-5xl font-bold text-primary tabular-nums">{currentOccupancy}%</span>
            <p className="text-sm text-muted-foreground mt-1">תפוס</p>
          </div>
        </div>

        {/* Chart with confidence band */}
        <div className="h-52 mt-4">
          {chartData.length === 0 ? (
            <div className="h-full flex items-center justify-center text-muted-foreground gap-2">
              <Loader2 className="w-5 h-5 animate-spin" /> טוען תחזית…
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="medianGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                  </linearGradient>
                  <filter id="blurFilter"><feGaussianBlur in="SourceGraphic" stdDeviation={blurAmount} /></filter>
                </defs>
                <XAxis
                  dataKey="hour" type="number" domain={[HOUR_MIN, HOUR_MAX]}
                  axisLine={false} tickLine={false}
                  tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }}
                  tickFormatter={(v) => fmtHour(v)} ticks={[6, 9, 12, 15, 18, 21]}
                />
                <YAxis domain={[0, 100]} hide />
                <Tooltip
                  formatter={(val: unknown, name: string) =>
                    name === "occ" ? [`${val}%`, "חציון"] : [`${(val as number[])[0]}–${(val as number[])[1]}%`, "טווח"]}
                  labelFormatter={(h) => fmtHour(Number(h))}
                  contentStyle={{ borderRadius: 12, fontSize: 12 }}
                />
                {/* P10–P90 confidence band */}
                <Area
                  type="monotone" dataKey="band" stroke="none"
                  fill="hsl(var(--primary))" fillOpacity={0.12} animationDuration={500} isAnimationActive
                />
                {/* median line */}
                <Area
                  type="monotone" dataKey="occ" stroke="hsl(var(--primary))" strokeWidth={3}
                  fill="url(#medianGrad)" animationDuration={500}
                />
                <ReferenceDot
                  x={animatedHour} y={currentOccupancy} r={7}
                  fill="hsl(var(--primary))" stroke="hsl(var(--card))" strokeWidth={3}
                  style={{ filter: velocity > 0.01 ? "url(#blurFilter)" : "none" }} isFront
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="flex items-center gap-2 mt-4 text-sm text-muted-foreground">
          <span className="w-2 h-2 rounded-full bg-primary" />
          <span>שעה נבחרת: {fmtHour(animatedHour)} · תפוסה חזויה {currentOccupancy}% (טווח 80%)</span>
        </div>
      </div>

      {/* Best time to arrive */}
      <div className="space-y-3">
        <h3 className="font-semibold text-sm text-muted-foreground">זמן מומלץ להגעה</h3>
        <div className="card-elevated p-4 flex items-center gap-4 border-2 border-primary/20">
          <div className="w-12 h-12 rounded-2xl bg-primary flex items-center justify-center">
            <Sparkles className="w-6 h-6 text-primary-foreground" />
          </div>
          <div className="flex-1 text-right">
            <h4 className="font-semibold text-base">{bestPoint ? fmtHour(bestPoint.hour) : "—"}</h4>
            <p className="text-sm text-muted-foreground">
              {bestPoint ? `${Math.max(0, 100 - bestPoint.occ)}% סיכוי למצוא חנייה מהר (השעה הפנויה ביותר)` : "טוען…"}
            </p>
          </div>
        </div>
        <div className="card-elevated p-4 flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-muted flex items-center justify-center">
            <CloudSun className="w-6 h-6 text-muted-foreground" />
          </div>
          <div className="flex-1 text-right">
            <h4 className="font-semibold text-base">{forecast?.isHoliday ? "יום חג" : "יום רגיל"}</h4>
            <p className="text-sm text-muted-foreground">
              {forecast?.isHoliday
                ? "בחגים התפוסה נמוכה משמעותית — קל יותר למצוא חנייה"
                : "התחזית מבוססת על דפוסי הביקוש ההיסטוריים של האזור"}
            </p>
          </div>
        </div>
      </div>

      <SmartInsights trendDelta={trendDelta} />
    </div>
  );
};

export default PredictionsView;
