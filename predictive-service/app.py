from flask import Flask, request, jsonify
import joblib
import pandas as pd
import holidays
from datetime import datetime, date as date_cls
from pathlib import Path

app = Flask(__name__)

# Quantile model bundle trained by train-process/train_model.py (XGBoost, chosen
# via research/model_research.ipynb). Predicts OCCUPANCY RATE (0..1) from
# timestamp-derivable features + lot capacity, so it works for a lot of any size:
#   free_slots = (1 - rate) * total_slots
# The bundle holds P10/P50/P90 models for a median forecast + confidence band.
MODEL_PATH = Path(__file__).resolve().parent / "parking_model.pkl"
bundle = joblib.load(MODEL_PATH)
MODELS = bundle["models"]            # {"p10":..., "p50":..., "p90":...}
FEATURES = bundle["features"]

IL_HOLIDAYS = holidays.Israel(years=range(2024, 2031), language="en_US")


def _features_for(dt: datetime, total_slots: int) -> dict:
    # Calendar-only (lot-agnostic). total_slots is applied after prediction to
    # scale the rate into a slot count, so it is intentionally not a model feature.
    return {
        "hour_of_day": dt.hour,
        "day_of_week": dt.weekday(),                       # Mon=0 .. Sun=6
        "is_weekend": 1 if dt.weekday() in (4, 5) else 0,  # Israeli weekend Fri/Sat
        "month": dt.month,
        "day_of_year": dt.timetuple().tm_yday,
        "is_holiday": 1 if dt.date() in IL_HOLIDAYS else 0,
    }


def _rate(model_key: str, feats: dict) -> float:
    X = pd.DataFrame([feats])[FEATURES]
    r = float(MODELS[model_key].predict(X)[0])
    return max(0.0, min(1.0, r))


def _cors(resp):
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type"
    resp.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    return resp


@app.route('/predict', methods=['POST', 'OPTIONS'])
def predict():
    if request.method == 'OPTIONS':
        return _cors(app.make_default_options_response())
    try:
        data = request.json
        current_free_slots = float(data.get('currentFreeSlots', 0) or 0)
        total_slots = int(data.get('totalSlots', 0) or 0)
        target_dt = datetime.fromisoformat(data.get('targetTimestamp').replace("Z", "+00:00"))

        feats = _features_for(target_dt, total_slots)
        rate = _rate("p50", feats)
        predicted_free = (1.0 - rate) * total_slots
        # smooth with current state (70/30) to avoid sharp jumps
        final = int(max(0, min(total_slots, round(0.7 * predicted_free + 0.3 * current_free_slots))))

        return _cors(jsonify({
            "predictedFreeSlots": final,
            "predictedOccupancyRate": round(rate, 3),
            "status": "success",
            "model_used": "XGBoost (P50)",
        })), 200
    except Exception as e:
        print(f"Error during prediction: {e}")
        return _cors(jsonify({"error": str(e)})), 500


@app.route('/forecast', methods=['POST', 'OPTIONS'])
def forecast():
    """Full-day hourly forecast for a date + lot capacity, with a P10-P90 band.
    Body: {"date":"YYYY-MM-DD", "totalSlots":N}. Returns 24 hourly points."""
    if request.method == 'OPTIONS':
        return _cors(app.make_default_options_response())
    try:
        data = request.json
        total_slots = int(data.get('totalSlots', 0) or 0)
        # accept either a plain date or a full timestamp
        raw = data.get('date') or data.get('targetTimestamp')
        d = datetime.fromisoformat(raw.replace("Z", "+00:00")).date() if "T" in raw \
            else date_cls.fromisoformat(raw)

        points = []
        for hour in range(24):
            dt = datetime(d.year, d.month, d.day, hour)
            feats = _features_for(dt, total_slots)
            r50 = _rate("p50", feats)
            r10 = _rate("p10", feats)   # low occupancy  -> high free
            r90 = _rate("p90", feats)   # high occupancy -> low free
            free_median = (1.0 - r50) * total_slots
            free_upper = (1.0 - r10) * total_slots   # optimistic (more free)
            free_lower = (1.0 - r90) * total_slots   # pessimistic (fewer free)
            lo, hi = sorted((free_lower, free_upper))
            points.append({
                "hour": hour,
                "occupancyPct": round(r50 * 100, 1),
                "freeMedian": int(round(max(0, min(total_slots, free_median)))),
                "freeLower": int(round(max(0, min(total_slots, lo)))),
                "freeUpper": int(round(max(0, min(total_slots, hi)))),
            })

        is_holiday = bool(d in IL_HOLIDAYS)
        return _cors(jsonify({
            "date": d.isoformat(),
            "totalSlots": total_slots,
            "isHoliday": is_holiday,
            "holidayName": IL_HOLIDAYS.get(d) if is_holiday else None,
            "points": points,
            "model_used": "XGBoost quantile (P10/P50/P90)",
        })), 200
    except Exception as e:
        print(f"Error during forecast: {e}")
        return _cors(jsonify({"error": str(e)})), 500


if __name__ == '__main__':
    print("Starting ML Prediction Service on port 5000...")
    app.run(host='0.0.0.0', port=5000)
