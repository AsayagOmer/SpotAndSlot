"""
================================================================================
Smart Parking - North Tel Aviv synthetic dataset generator
================================================================================
Generates a large, realistic hourly parking time-series for several real-world
North Tel Aviv parking lots, suitable for training and comparing ML models.

Why this is better than a naive generator:
  * MULTIPLE lots with DIFFERENT capacities -> `total_slots` becomes a real,
    learnable feature (the previous dataset hard-coded 150 for every row, so the
    model could learn nothing from it).
  * Distinct demand "personalities" per lot type (university / mall / leisure /
    park / residential) with realistic hour-of-day and day-of-week shapes.
  * Real seasonality: summer beach season, winter rain, academic calendar.
  * REAL special events that actually move parking demand in North Tel Aviv:
      - Israeli public holidays (via the `holidays` library) incl. Yom Kippur
        (the country shuts down), Rosh Hashana, Passover, Sukkot, etc.
      - Chol HaMoed (intermediate holiday days) -> leisure spikes.
      - Tel Aviv University academic calendar (semesters, exam periods, break).
      - Tel Aviv Marathon, "White Night" (Laila Lavan), Independence Day,
        summer beach peak, Tel Aviv Port food/culture weekends.
      - Rain days (winter) that suppress outdoor leisure but help malls.
  * Stochastic realism: hour-to-hour autocorrelation, daily anomaly multipliers,
    and white noise -- clipped to valid occupancy.

The script is deterministic (fixed seed) so results are reproducible.

Output columns:
  timestamp, lot_id, lot_name, lot_type, total_slots,
  hour_of_day, day_of_week, is_weekend, month, day_of_month, week_of_year,
  season, is_holiday, holiday_name, is_chol_hamoed, academic_term,
  is_special_event, event_name, temperature_c, is_rain,
  occupancy_rate, occupied_slots, free_slots
Target for modelling: `free_slots` (or `occupancy_rate`).
================================================================================
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import holidays

SEED = 42
rng = np.random.default_rng(SEED)

# One full year: a Tel Aviv University academic year + a full summer season.
START = pd.Timestamp("2024-09-01 00:00:00")
END = pd.Timestamp("2025-08-31 23:00:00")

# ---------------------------------------------------------------------------
# The lots: real North Tel Aviv parking locations with realistic capacities.
# ---------------------------------------------------------------------------
LOTS = [
    {"lot_id": "1", "lot_name": "Tel Aviv University (Ramat Aviv)", "lot_type": "university", "total_slots": 420},
    {"lot_id": "2", "lot_name": "Ramat Aviv Mall",                  "lot_type": "mall",       "total_slots": 600},
    {"lot_id": "3", "lot_name": "Tel Aviv Port (Namal)",            "lot_type": "leisure",    "total_slots": 300},
    {"lot_id": "4", "lot_name": "Yarkon Park / Reading",            "lot_type": "park",       "total_slots": 180},
    {"lot_id": "5", "lot_name": "Bavli Residential",                "lot_type": "residential","total_slots": 120},
]

IL_HOLIDAYS = holidays.Israel(years=[2024, 2025], language="en_US")


# ---------------------------------------------------------------------------
# Helpers: calendar context
# ---------------------------------------------------------------------------
def season_of(month: int) -> str:
    if month in (12, 1, 2):
        return "winter"
    if month in (3, 4, 5):
        return "spring"
    if month in (6, 7, 8):
        return "summer"
    return "autumn"


def academic_term(ts: pd.Timestamp) -> str:
    """Tel Aviv University academic calendar (approximate, typical year)."""
    d = ts.date()
    y, m = d.year, d.month
    # Semester A: mid-Oct -> late-Jan ; exams late-Jan -> Feb
    # Semester B: mid-Mar -> late-Jun ; exams Jul ; summer break Aug -> mid-Oct
    if (m == 10 and d.day >= 20) or m in (11, 12) or (m == 1 and d.day <= 20):
        return "semester_a"
    if (m == 1 and d.day > 20) or m == 2:
        return "exams_a"
    if (m == 3 and d.day >= 16) or m in (4, 5) or (m == 6 and d.day <= 25):
        return "semester_b"
    if (m == 6 and d.day > 25) or m == 7:
        return "exams_b"
    return "break"  # Aug -> mid-Oct, plus early-Mar gap


def special_event(ts: pd.Timestamp):
    """Return (event_name or None) for North Tel Aviv flagship events."""
    d = ts.date()
    m, day = d.month, d.day
    # Tel Aviv Marathon - last Friday of February (approx 28 Feb 2025)
    if m == 2 and 26 <= day <= 28 and ts.dayofweek == 4:
        return "tlv_marathon"
    # Independence Day eve/day (Yom Ha'atzmaut ~ 1 May 2025) - beaches & parks packed
    if (m == 4 and day == 30) or (m == 5 and day == 1):
        return "independence_day"
    # "White Night" / Laila Lavan - city-wide nightlife (late June)
    if m == 6 and day == 26:
        return "white_night"
    # Summer beach peak weekends (July-August Fridays/Saturdays)
    if m in (7, 8) and ts.dayofweek in (4, 5):
        return "summer_beach_peak"
    # Tel Aviv Port food & culture weekends (one weekend a month, shoulder seasons)
    if m in (3, 4, 5, 9, 10) and ts.dayofweek == 5 and 8 <= day <= 14:
        return "port_festival"
    return None


# ---------------------------------------------------------------------------
# Base demand "shape" per lot type: hour-of-day x day-of-week occupancy in [0,1]
# day_of_week: Mon=0 ... Fri=4, Sat=5, Sun=6  (Israeli weekend = Fri+Sat)
# ---------------------------------------------------------------------------
def base_occupancy(lot_type: str, hour: int, dow: int) -> float:
    weekend = dow in (4, 5)
    fri = dow == 4
    sat = dow == 5

    if lot_type == "university":
        if weekend:
            return 0.10 if (10 <= hour <= 15 and fri) else 0.04
        if hour < 7:
            return 0.05
        if 7 <= hour < 10:
            return 0.55
        if 10 <= hour < 16:
            return 0.92
        if 16 <= hour < 20:
            return 0.55
        return 0.15

    if lot_type == "mall":
        # malls: busy evenings, very busy Fri daytime; quieter Sat morning, busy Sat eve
        if hour < 9:
            return 0.05
        if fri:
            return 0.85 if 10 <= hour <= 15 else (0.45 if hour < 18 else 0.15)
        if sat:
            return 0.25 if hour < 18 else 0.80  # Saturday evening rush after Shabbat
        if 9 <= hour < 16:
            return 0.45
        if 16 <= hour < 22:
            return 0.80
        return 0.20

    if lot_type == "leisure":  # Tel Aviv Port
        if hour < 8:
            return 0.06
        if weekend:
            return 0.55 if hour < 11 else (0.92 if hour < 22 else 0.50)
        if 8 <= hour < 17:
            return 0.35
        if 17 <= hour < 24:
            return 0.70
        return 0.15

    if lot_type == "park":  # Yarkon / Reading
        if hour < 7:
            return 0.04
        if weekend:
            return 0.80 if 8 <= hour <= 18 else 0.20
        if 7 <= hour < 9:
            return 0.35  # morning runners
        if 16 <= hour < 20:
            return 0.55  # afternoon
        return 0.20

    # residential: inverse commuter pattern (full at night, empties by day)
    if hour < 6 or hour >= 21:
        return 0.85
    if 6 <= hour < 9:
        return 0.70
    if 9 <= hour < 17:
        return 0.35
    return 0.65


def event_multiplier(lot_type: str, event: str | None, is_holiday: bool,
                     is_chol_hamoed: bool, holiday_name: str | None) -> float:
    """How special events / holidays scale a lot's demand."""
    mult = 1.0

    # Yom Kippur: the entire country stops -> near-zero everywhere.
    if holiday_name and "Yom Kippur" in holiday_name:
        return 0.02

    if is_holiday:
        # On most chagim, workplaces/universities/malls are closed or quiet,
        # while leisure/park can be busy (people are off).
        if lot_type in ("university", "mall"):
            mult *= 0.35
        elif lot_type in ("leisure", "park"):
            mult *= 1.15
        else:
            mult *= 1.05  # residential slightly fuller (people home)

    if is_chol_hamoed and lot_type in ("leisure", "park", "mall"):
        mult *= 1.30  # intermediate holiday days -> outings

    if event == "tlv_marathon" and lot_type in ("park", "leisure"):
        mult *= 1.45
    elif event == "independence_day" and lot_type in ("park", "leisure"):
        mult *= 1.55
    elif event == "white_night" and lot_type in ("leisure", "mall"):
        mult *= 1.40
    elif event == "summer_beach_peak" and lot_type in ("leisure", "park"):
        mult *= 1.35
    elif event == "port_festival" and lot_type == "leisure":
        mult *= 1.30

    return mult


def academic_multiplier(lot_type: str, term: str) -> float:
    if lot_type != "university":
        return 1.0
    return {
        "semester_a": 1.0, "semester_b": 1.0,
        "exams_a": 0.55, "exams_b": 0.45,  # many exams online / fewer on campus
        "break": 0.25,
    }.get(term, 1.0)


def weather_for(ts: pd.Timestamp):
    """Seasonal temperature + rain probability (Tel Aviv coastal climate)."""
    doy = ts.dayofyear
    # temperature: warm Mediterranean, peak ~Aug, trough ~Jan
    temp = 21 + 9 * np.sin(2 * np.pi * (doy - 110) / 365) + rng.normal(0, 1.5)
    # rain mostly Nov-Mar
    m = ts.month
    rain_p = {11: 0.18, 12: 0.30, 1: 0.32, 2: 0.28, 3: 0.18, 4: 0.06}.get(m, 0.01)
    is_rain = 1 if rng.random() < rain_p else 0
    return round(float(temp), 1), is_rain


def weather_multiplier(lot_type: str, is_rain: int, temp: float) -> float:
    mult = 1.0
    if is_rain:
        if lot_type in ("park", "leisure"):
            mult *= 0.55
        elif lot_type == "mall":
            mult *= 1.15  # rainy day -> indoor mall
    if temp >= 30 and lot_type in ("leisure", "park"):
        mult *= 1.10  # hot day -> beach/park (esp. with sea breeze)
    return mult


# ---------------------------------------------------------------------------
# Main generation loop
# ---------------------------------------------------------------------------
def generate() -> pd.DataFrame:
    timestamps = pd.date_range(START, END, freq="h")

    # one random daily anomaly multiplier per (date) shared across lots (e.g. a
    # city-wide quiet/busy day not otherwise modelled)
    dates = pd.Series(timestamps.date).unique()
    daily_anomaly = {
        d: float(rng.choice([1.0, 0.8, 1.2, 1.35], p=[0.80, 0.08, 0.07, 0.05]))
        for d in dates
    }

    rows = []
    for lot in LOTS:
        prev = base_occupancy(lot["lot_type"], 8, 0)  # seed autocorrelation
        for ts in timestamps:
            hour = ts.hour
            dow = int(ts.dayofweek)
            month = ts.month
            date = ts.date()

            hol_name = IL_HOLIDAYS.get(date)
            is_holiday = 1 if hol_name else 0
            # Chol HaMoed: a holiday-adjacent weekday during Sukkot/Passover windows
            is_chol = 1 if (not is_holiday and _in_chol_hamoed(date)) else 0
            term = academic_term(ts)
            event = special_event(ts)
            temp, is_rain = weather_for(ts)

            base = base_occupancy(lot["lot_type"], hour, dow)
            base *= academic_multiplier(lot["lot_type"], term)
            base *= event_multiplier(lot["lot_type"], event, bool(is_holiday),
                                     bool(is_chol), hol_name)
            base *= weather_multiplier(lot["lot_type"], is_rain, temp)
            base *= daily_anomaly[date]

            # autocorrelation with previous hour + white noise
            occ = 0.65 * base + 0.35 * prev + rng.normal(0, 0.05)
            occ = float(np.clip(occ, 0.0, 1.0))
            prev = occ

            occupied = int(round(occ * lot["total_slots"]))
            free = lot["total_slots"] - occupied

            rows.append({
                "timestamp": ts.isoformat(),
                "lot_id": lot["lot_id"],
                "lot_name": lot["lot_name"],
                "lot_type": lot["lot_type"],
                "total_slots": lot["total_slots"],
                "hour_of_day": hour,
                "day_of_week": dow,
                "is_weekend": 1 if dow in (4, 5) else 0,
                "month": month,
                "day_of_month": ts.day,
                "week_of_year": int(ts.isocalendar().week),
                "season": season_of(month),
                "is_holiday": is_holiday,
                "holiday_name": hol_name or "",
                "is_chol_hamoed": is_chol,
                "academic_term": term,
                "is_special_event": 1 if event else 0,
                "event_name": event or "",
                "temperature_c": temp,
                "is_rain": is_rain,
                "occupancy_rate": round(occ, 4),
                "occupied_slots": occupied,
                "free_slots": free,
            })

    return pd.DataFrame(rows)


# Approximate Chol HaMoed windows (intermediate days) for the modelled year.
_CHOL_HAMOED_RANGES = [
    (pd.Timestamp("2024-10-18"), pd.Timestamp("2024-10-23")),  # Sukkot 2024
    (pd.Timestamp("2025-04-14"), pd.Timestamp("2025-04-18")),  # Passover 2025
    (pd.Timestamp("2025-10-08"), pd.Timestamp("2025-10-12")),  # Sukkot 2025 (tail)
]


def _in_chol_hamoed(date) -> bool:
    ts = pd.Timestamp(date)
    return any(lo <= ts <= hi for lo, hi in _CHOL_HAMOED_RANGES)


if __name__ == "__main__":
    print("Generating North Tel Aviv parking dataset...")
    df = generate()
    out = "parking_north_tlv.csv"
    df.to_csv(out, index=False)
    print(f"Rows: {len(df):,} | lots: {df.lot_id.nunique()} | "
          f"range: {df.timestamp.min()} .. {df.timestamp.max()}")
    print(f"Holidays flagged: {df.is_holiday.sum():,} rows | "
          f"special-event rows: {df.is_special_event.sum():,}")
    print(f"Saved to {out}")
