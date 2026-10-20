# Smart Parking — ML Prediction Service

Flask/XGBoost microservice that forecasts parking availability for the Smart
Parking system. Part of a three-repo setup:

| Repository | Purpose |
|---|---|
| [`2026b.omer.asayag`](https://bitbucket.org/tomcatafeka/2026b.omer.asayag) | Spring Boot API (calls `/predict` from the `predictAvailability` command) |
| [`2026b.omer.asayag_client`](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client) | Spot-Insight GUI (calls `/forecast` for the Predictions tab, via the Vite `/ml` proxy) |
| **this repo** | The prediction microservice |

## How it works

`parking_model.pkl` is an XGBoost **quantile bundle** (P10 / P50 / P90) trained
by `train-process/train_model.py` on the dataset generated in `data-gen/`
(model selection documented in `research/model_research.ipynb`). It predicts an
**occupancy rate** (0..1) from calendar features only:

- hour of day, day of week, Israeli weekend (Fri/Sat), month, day of year
- Israeli public-holiday flag (`holidays` package)

The rate is scaled by the caller's lot capacity —
`free = (1 - rate) * totalSlots` — so **one model serves any number of lots of
any size** (multi-lot ready by design; the lot's capacity is passed per request
and is deliberately not a model feature).

## Endpoints

| Endpoint | Body | Returns |
|---|---|---|
| `POST /predict` | `{"targetTimestamp": ISO-8601, "currentFreeSlots": N, "totalSlots": N}` | single-point prediction (P50, smoothed 70/30 with the current state) |
| `POST /forecast` | `{"date": "YYYY-MM-DD", "totalSlots": N}` | 24 hourly points with a P10–P90 confidence band + holiday info |

Sample requests live in `curl/`.

## Run

Written for a **clean Linux (Ubuntu/Debian) machine** — nothing pre-installed.
Requires Python 3.10+.

### 0. One-time machine setup

```sh
sudo apt-get update
sudo apt-get install -y python3 python3-pip python3-venv
python3 --version   # verify: 3.10 or newer
```

### 1. Create a virtual environment and install dependencies

Modern Debian/Ubuntu **blocks global `pip install`** ("externally-managed-
environment"), so always use a virtual environment:

```sh
python3 -m venv .venv             # first time only
. .venv/bin/activate              # every new terminal (prompt shows "(.venv)")
pip install -r requirements.txt   # first time only; downloads xgboost etc., takes a few minutes
```

### 2. Start the service

```sh
python app.py                     # serves on http://localhost:5000
```

Verify from a second terminal:

```sh
curl -s -X POST http://localhost:5000/forecast \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-07-02","totalSlots":33}'
# expect: JSON with 24 hourly forecast points
```

(On Windows, the venv activation is `.venv\Scripts\activate`; everything else
is identical.)

If the service is down, the API's `predictAvailability` command gracefully
falls back to the current free-slot count, and the GUI's Predictions tab shows
a connection state.

## Retrain

Run inside the same virtual environment (`. .venv/bin/activate`):

```sh
python data-gen/mockdatagen.py          # regenerate the synthetic dataset
python train-process/train_model.py     # retrain and write parking_model.pkl
```
