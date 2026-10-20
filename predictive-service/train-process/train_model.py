"""
================================================================================
Train the production parking-availability model (quantile forecaster).
================================================================================
Model choice backed by ../research/model_research.ipynb (XGBoost won).

Design for a robust, realistic-looking production forecaster:

  1. Lot-agnostic occupancy curve. At inference the service only knows the target
     time and the lot's capacity - not the lot's "type". If capacity were a model
     feature, an unseen capacity would snap to whichever trained lot is nearest in
     size (e.g. a small lot wrongly inheriting the residential "full at night"
     pattern). So the model predicts OCCUPANCY RATE from calendar signal only and
     capacity is applied afterwards:  free_slots = (1 - rate) * total_slots.
     We train on the standard daytime-demand lots (university/mall/leisure/park);
     the residential inverse-occupancy class is excluded as it is a different
     demand profile. (The research notebook still studies all lots & features.)

  2. Finer time signal: `day_of_year` lets the model produce a different curve for
     each specific date - real week-to-week and day-to-day variation instead of
     one fixed shape per (weekday, month).

  3. Uncertainty band: three quantile models (P10 / P50 / P90) via XGBoost's
     quantile objective, so the service returns a median forecast + a confidence
     band (more honest and far better looking).
================================================================================
"""
from pathlib import Path
import pandas as pd
from xgboost import XGBRegressor
import joblib

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / "data-gen" / "parking_north_tlv.csv"
MODEL_OUT = HERE.parent / "parking_model.pkl"

# Calendar-only features (lot-agnostic). Keep in sync with app.py.
FEATURES = ["hour_of_day", "day_of_week", "is_weekend", "month", "day_of_year", "is_holiday"]
TARGET = "occupancy_rate"
QUANTILES = {"p10": 0.10, "p50": 0.50, "p90": 0.90}
EXCLUDE_LOT_TYPES = {"residential"}  # inverse-occupancy class, not a standard lot

print(f"Loading {DATA} ...")
df = pd.read_csv(DATA, parse_dates=["timestamp"])
df = df[~df["lot_type"].isin(EXCLUDE_LOT_TYPES)].copy()
df["day_of_year"] = df["timestamp"].dt.dayofyear
X, y = df[FEATURES], df[TARGET]
print(f"Training on {len(df):,} rows from lot types: {sorted(df.lot_type.unique())}")

models = {}
for name, alpha in QUANTILES.items():
    print(f"Training XGBoost quantile model {name} (alpha={alpha}) ...")
    m = XGBRegressor(
        objective="reg:quantileerror", quantile_alpha=alpha,
        n_estimators=500, learning_rate=0.06, max_depth=7,
        subsample=0.9, colsample_bytree=0.9,
        n_jobs=-1, random_state=42, tree_method="hist",
    )
    m.fit(X, y)
    models[name] = m

joblib.dump({"models": models, "features": FEATURES, "target": TARGET}, MODEL_OUT)
print(f"Saved quantile model bundle (P10/P50/P90) to {MODEL_OUT}")
