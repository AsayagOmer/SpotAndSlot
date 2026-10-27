from flask import Flask, request, jsonify
import joblib
import math
import os
import pandas as pd
import holidays
from datetime import datetime, date as date_cls, timezone
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
ALLOWED_ORIGIN = os.environ.get("CORS_ALLOWED_ORIGIN", "*")


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
    resp.headers["Access-Control-Allow-Origin"] = ALLOWED_ORIGIN
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type"
    resp.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    return resp


def _parse_iso_datetime(raw: str) -> datetime:
    if not raw or not isinstance(raw, str) or not raw.strip():
        raise ValueError("targetTimestamp is required and must be a non-empty ISO-8601 string")
    cleaned = raw.strip().replace("Z", "+00:00")
    return datetime.fromisoformat(cleaned)


@app.route('/health', methods=['GET'])
def health():
    return _cors(jsonify({
        "status": "healthy",
        "model_loaded": bool(MODELS and all(k in MODELS for k in ("p10", "p50", "p90"))),
        "features": FEATURES,
    })), 200


@app.route('/predict', methods=['POST', 'OPTIONS'])
def predict():
    if request.method == 'OPTIONS':
        return _cors(app.make_default_options_response())
    try:
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            return _cors(jsonify({"error": "Request body must be a valid JSON object"})), 400

        try:
            total_slots = int(data.get('totalSlots', 0) if data.get('totalSlots') is not None else 0)
            current_free_slots = float(
                data.get('currentFreeSlots', 0) if data.get('currentFreeSlots') is not None else 0
            )
        except (TypeError, ValueError):
            return _cors(jsonify({"error": "totalSlots and currentFreeSlots must be numeric"})), 400

        if total_slots < 0 or current_free_slots < 0:
            return _cors(jsonify({"error": "totalSlots and currentFreeSlots must be non-negative"})), 400

        try:
            target_dt = _parse_iso_datetime(data.get('targetTimestamp'))
        except ValueError as ve:
            return _cors(jsonify({"error": str(ve)})), 400

        feats = _features_for(target_dt, total_slots)
        r50 = _rate("p50", feats)
        r10 = _rate("p10", feats)
        r90 = _rate("p90", feats)

        predicted_free_p50 = (1.0 - r50) * total_slots
        free_upper = (1.0 - r10) * total_slots
        free_lower = (1.0 - r90) * total_slots
        lo, hi = sorted((free_lower, free_upper))

        # Exponential time-horizon decay blending:
        # Near-term predictions (< 2h ahead) blend live state (up to 50% weight);
        # far-horizon predictions (> 6h ahead) rely primarily on the XGBoost P50 prior.
        now_dt = datetime.now(target_dt.tzinfo) if target_dt.tzinfo else datetime.now()
        delta_hours = max(0.0, (target_dt - now_dt).total_seconds() / 3600.0)
        live_weight = 0.5 * math.exp(-delta_hours / 2.0)

        blended = (1.0 - live_weight) * predicted_free_p50 + live_weight * current_free_slots
        final = int(max(0, min(total_slots, round(blended))))
        lower_bound = int(max(0, min(total_slots, round(lo))))
        upper_bound = int(max(0, min(total_slots, round(hi))))

        return _cors(jsonify({
            "predictedFreeSlots": final,
            "predictedFreeLower": min(lower_bound, final),
            "predictedFreeUpper": max(upper_bound, final),
            "predictedOccupancyRate": round(r50, 3),
            "liveBlendWeight": round(live_weight, 3),
            "status": "success",
            "model_used": "XGBoost quantile (P10/P50/P90)",
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
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            return _cors(jsonify({"error": "Request body must be a valid JSON object"})), 400

        try:
            total_slots = int(data.get('totalSlots', 0) if data.get('totalSlots') is not None else 0)
        except (TypeError, ValueError):
            return _cors(jsonify({"error": "totalSlots must be an integer"})), 400

        if total_slots < 0:
            return _cors(jsonify({"error": "totalSlots must be non-negative"})), 400

        # accept either a plain date or a full timestamp
        raw = data.get('date') or data.get('targetTimestamp')
        if not raw or not isinstance(raw, str) or not raw.strip():
            return _cors(jsonify({"error": "date or targetTimestamp is required"})), 400

        try:
            cleaned = raw.strip().replace("Z", "+00:00")
            d = datetime.fromisoformat(cleaned).date() if "T" in cleaned else date_cls.fromisoformat(cleaned)
        except ValueError as ve:
            return _cors(jsonify({"error": f"Invalid date/timestamp format: {ve}"})), 400

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
