# Smart Parking — ML Prediction Service

Flask/XGBoost microservice that forecasts parking availability for the Smart
Parking system.

## How it works

`parking_model.pkl` is an XGBoost **quantile bundle** (P10 / P50 / P90) trained
by `train-process/train_model.py` on the dataset generated in `data-gen/`
(model selection documented in `research/model_research.ipynb`). It predicts an
**occupancy rate** (0..1) from calendar features:

- hour of day, day of week, Israeli weekend (Fri/Sat), month, day of year
- Israeli public-holiday flag (`holidays` package)

The rate is scaled by the caller's lot capacity —
`free = (1 - rate) * totalSlots` — so **one model serves any number of lots of
any size** (multi-lot ready by design; the lot's capacity is passed per request
and is deliberately not a model feature).

### Exponential Time-Horizon Decay Blending
For single-point predictions (`POST /predict`), the service blends the historical XGBoost P50 prediction with the lot's live `currentFreeSlots` using an exponential decay weight over the prediction horizon $\Delta t$ (in hours):
$$w(\Delta t) = 0.5 \cdot e^{-\max(0, \Delta t_{\text{hours}}) / 2.0}$$
$$\hat{y} = (1 - w(\Delta t)) \cdot \text{predictedFree}_{P50} + w(\Delta t) \cdot \text{currentFreeSlots}$$

## Endpoints

| Endpoint | Method | Body / Params | Returns |
|---|---|---|---|
| `/health` | `GET` | — | `{"status": "healthy", "model_loaded": true, "features": [...]}` |
| `/predict` | `POST` | `{"targetTimestamp": ISO-8601, "currentFreeSlots": N, "totalSlots": N}` | P50 prediction blended via exponential time-decay + P10/P90 bounds (`predictedFreeLower`, `predictedFreeUpper`) |
| `/forecast` | `POST` | `{"date": "YYYY-MM-DD", "totalSlots": N}` | 24 hourly points with a P10–P90 confidence band + holiday info |

Invalid payloads (missing/malformed timestamps or negative slot counts) are rejected with `400 Bad Request`. Sample requests live in `curl/`.

## Run & Test

Requires Python 3.10+.

```sh
python3 -m venv .venv
. .venv/bin/activate              # Windows: .venv\Scripts\activate
pip install -r requirements.txt pytest
python app.py                     # serves on http://localhost:5000
```

Run the automated unit test suite:

```sh
pytest -v
```

## Retrain

```sh
python data-gen/mockdatagen.py          # regenerate the synthetic dataset
python train-process/train_model.py     # retrain and write parking_model.pkl
```
