# Smart Parking — Server + Spot-Insight GUI (Integration Notes)

The Smart Parking system is split across three repositories:

| Repository | What it is |
|---|---|
| `2026b.omer.asayag` (this repo) | **Smart Parking API** — the main project. Spring Boot server, JPA database (Postgres / in-memory H2), REST API. |
| [`2026b.omer.asayag_client`](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client) | **Spot-Insight GUI** — React + Vite web client that consumes the API. |
| [`2026b.omer.asayag_predictive_service`](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_predictive_service) | Python ML microservice used by the `predictAvailability` command. |

The `docs/` folder (SRS, Software Test Report, ML Model Evaluation Report) stays local.

> Paths written below as `client/...` are relative to the **client repo**, and
> `prediction-service/...` paths are relative to the **prediction-service repo**.
> The run steps assume the three repos are cloned side by side as sibling directories.

## Architecture

```
┌──────────────────────────┐         REST / JSON                    ┌──────────────────────────────┐
│  Spot-Insight (client/)   │  ────────────────────────────────────▶ │  Smart Parking API (root)    │
│  Vite + React + shadcn    │  GET  /ambient-invisible-intelligence/ │  Spring Boot, JPA DB         │
│  http://localhost:8080    │       objects                          │  http://localhost:8084       │
│                           │  POST /ambient-invisible-intelligence/ │  system id 2026b.Omer.Asayag │
└──────────────────────────┘       commands                         └──────────────────────────────┘
        ▲                                                                     │
        │ live slots + predictions                                           │ predictAvailability
        └─────────────────────────────────────────────────────────────────  ▼  http://localhost:5000
                                                              prediction-service/ (Python ML)
```

## How they connect

| Concern | Where |
|---|---|
| API base URL & identity | `client/.env` (`VITE_API_BASE_URL`, `VITE_SYSTEM_ID`, `VITE_USER_EMAIL`) |
| Typed API client (objects, commands, users, admin) | `client/src/lib/api.ts` |
| Data fetching + mapping | `client/src/hooks/useParkingData.ts` |
| Live parking map | `client/src/components/ParkingLotMap.tsx` (polls every 5 s) |
| Prediction tab | `client/src/components/PredictionsView.tsx` (full-day forecast + band) |
| Admin console | `client/src/pages/Admin.tsx` (route `/admin`; users, activity, health) |
| CORS (server allows the GUI origin) | `src/main/java/ambient_invisible_intelligence/config/WebConfig.java` |
| ML forecast proxy (browser → ML service, same-origin) | `client/vite.config.ts` (`/ml` → `http://localhost:5000`) |

### The two ways the GUI reaches data

1. **API (Spring) — `VITE_API_BASE_URL`**: live objects (`GET /objects`),
   commands (`POST /commands`), and admin (`GET /admin/users`,
   `GET /admin/commands`). The live map, the admin console, and the
   `predictAvailability` calls all go here.
2. **ML service `/forecast` — via the Vite `/ml` proxy**: the Predictions tab
   fetches a full-day curve (median + P10–P90 band) in one call from the Python
   service. The proxy keeps it same-origin so no extra CORS is needed in dev.

### Data mapping (API → GUI)

| API `PARKING_SLOT` | GUI spot status |
|---|---|
| `slotKind=REGULAR`, `status=FREE` | available |
| `slotKind=REGULAR`, `status=OCCUPIED` | occupied |
| `slotKind=HANDICAP`, `status=FREE` | disabled-available |
| `slotKind=HANDICAP`, `status=OCCUPIED` | disabled-occupied |

Slots are grouped by `parentSectionId` and rendered one block per section.
Occupancy %, the "last updated" indicator, and the live status all come from real API responses.
The server seeds three demo lots (owned by two different admins, 65 slots in
total) on startup under the `initDemos` profile.

## Running the combined system

Full from-scratch install instructions for a clean Linux machine (JDK, Node,
Python) are in each repo's README; the short version:

### 1. Start the API (main project) — no database installation needed

```sh
# from the repository root (JDK 17+ required; the wrapper downloads the rest)
./gradlew bootRun --args="--spring.docker.compose.enabled=false"
```

This runs against an in-memory H2 database and seeds the demo data. If Docker
is installed, plain `./gradlew bootRun` starts Postgres via `compose.yaml`
instead. The API serves on **http://localhost:8084/ambient-invisible-intelligence**.

### 2. (optional) Start the prediction microservice

```sh
cd ../2026b.omer.asayag_predictive_service
sudo apt-get install -y python3 python3-pip python3-venv   # first time only
python3 -m venv .venv && . .venv/bin/activate              # first time only
pip install -r requirements.txt                            # first time only
python app.py                                              # serves on :5000
```

See the prediction-service repo's `README.md` for the data → train → serve workflow.
The service exposes `/predict` (single point, used by the `predictAvailability`
command) and `/forecast` (full-day curve with a P10–P90 band, used by the GUI's
Predictions tab). If it is not running, `predictAvailability` gracefully falls
back to the current free-slot count and the Predictions tab shows a connection state.

### 3. Start the clients (Node.js 18+ required — see the client repo README)

```sh
cd ../2026b.omer.asayag_client
npm install          # first time only
npm run dev:mobile   # end-user app  -> http://localhost:8080
npm run dev:admin    # admin console -> http://localhost:8090  (second terminal)
```

- **Mobile app** (`:8080`, also built as an Android APK) — sign in
  (`user@demo.org` / `User@1`), pick a lot, watch the live map (polled ~5 s)
  and the ML forecast on the Predictions tab.
- **Admin console** (`:8090`) — sign in as an admin (`admin@demo.org` /
  `Admin@1`); dashboard, lot/section/slot management, users, activity feed and
  per-lot health checks — scoped to the lots that admin owns.

## Notes

- To point the clients at a different server, edit the client repo's `.env`
  and restart the dev server (the mobile app also has an in-app server-address
  setting on its login screen).
- If the API is **not running**, the clients show a clear "no connection" state with a retry button.
- Add more allowed origins in `WebConfig.java` if you deploy the clients elsewhere.
