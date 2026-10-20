# Smart Parking API

Spring Boot API for the Ambient Invisible Intelligence project. The GUI client and
the ML prediction service now live in their own repositories:

| Repository | Purpose |
|---|---|
| **this repo** (`2026b.omer.asayag`) | Spring Boot API, JPA persistence (Postgres / in-memory H2), command logic, demo seeding |
| [`2026b.omer.asayag_client`](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client) | Spot-Insight GUI, built with React, Vite, shadcn/ui, and React Query |
| [`2026b.omer.asayag_predictive_service`](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_predictive_service) | Flask/XGBoost ML microservice used by `predictAvailability` |

Contents of this repo:

| Path | Purpose |
|---|---|
| `src/`, `build.gradle` | Spring Boot API source and build |
| `INTEGRATION.md` | GUI/API integration notes |
| `LIVE-VERIFICATION.md` | Manual live verification checklist |

The run instructions below assume all three repos are cloned side by side as sibling
directories (`2026b.omer.asayag`, `2026b.omer.asayag_client`,
`2026b.omer.asayag_predictive_service`).

## Runtime Contract

| Service | URL / Value |
|---|---|
| API base URL | `http://localhost:8084/ambient-invisible-intelligence` |
| OpenAPI UI | `http://localhost:8084/swagger-ui.html` |
| API version header | `API-Version: 1.4` |
| `systemID` | `2026b.Omer.Asayag` |
| GUI dev server | `http://localhost:8080` |
| ML prediction service | `http://localhost:5000` (`/predict`, `/forecast`) |
| Database | in-memory H2 by default; Postgres via `compose.yaml` when Docker is available |

The GUI defaults are in the client repo's `.env.example`; local overrides can be
placed in the client repo's `.env`.

## Requirements

Everything below is written for a **clean Linux (Ubuntu/Debian) machine** and
assumes nothing is pre-installed. Each component states exactly what it needs
and how to install it. (On Windows, use `gradlew.bat` instead of `./gradlew`.)

| Component | Needs | Installed by |
|---|---|---|
| API (this repo) | JDK 17+ on PATH, internet access | `apt` (JDK); the Gradle wrapper downloads Gradle itself and the Java 25 compile toolchain automatically |
| Database | nothing (in-memory H2 fallback) or Docker (Postgres) | — / Docker Engine |
| ML service | Python 3.10+, pip, venv | pre-installed on the dockerV4 vlab; otherwise `apt` (step 2 below) |
| Web clients | Node.js 18+ and npm | `nvm` or `apt` (step 3 below) |

## Run The System

### 0. One-time machine setup (clean Ubuntu/Debian)

```sh
sudo apt-get update
sudo apt-get install -y git curl openjdk-21-jdk
java -version    # must print 17 or newer; 21 recommended
```

Notes that remove the usual surprises:

- **No Gradle installation is needed.** The repo ships the Gradle wrapper;
  the first `./gradlew` run downloads Gradle and the exact JDK 25 toolchain
  used for compilation (this requires internet access and takes a few
  minutes the first time).
- If `./gradlew` prints "Permission denied", run `chmod +x gradlew` once.
- **No database installation is needed** for running the demo — see step 2.

Clone all three repositories side by side:

```sh
git clone https://bitbucket.org/tomcatafeka/2026b.omer.asayag.git
git clone https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client.git
git clone https://bitbucket.org/tomcatafeka/2026b.omer.asayag_predictive_service.git
```

### 1. Start The API

From this repository's root — **works with nothing but the JDK installed**
(uses an in-memory H2 database and seeds the demo data on startup):

```sh
./gradlew bootRun --args="--spring.docker.compose.enabled=false"
```

Wait for `Started Application`; the API is then on `http://localhost:8084`.

Verify from a second terminal:

```sh
curl -H "API-Version: 1.4" "http://localhost:8084/ambient-invisible-intelligence/objects?userEmail=operator@demo.org&userPassword=Oper@1&size=100"
# expect: a JSON array with the seeded demo lots
```

**Alternative — persistent Postgres via Docker** (optional; only if Docker is
installed and running): plain `./gradlew bootRun` uses `compose.yaml` to start
Postgres automatically. To install Docker on Ubuntu:
`sudo apt-get install -y docker.io && sudo systemctl start docker` (add your
user to the `docker` group or run gradle with `sudo -E`).

Note: the in-memory database is wiped on every restart (dev/demo profile);
the seeded users and lots reappear automatically.

### 2. Start The ML Prediction Service

From the [prediction-service repo](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_predictive_service)
(full from-scratch install steps, including Python/pip/venv, are in that
repo's README):

```sh
cd ../2026b.omer.asayag_predictive_service

# Python is already installed on the dockerV4 vlab machine, so you can skip the
# next line there. If you're on your own machine and don't have Python yet, this
# one command gets you started (Python 3, pip, and the venv module):
sudo apt-get install -y python3 python3-pip python3-venv

python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
python app.py
```

The service loads `parking_model.pkl` (an XGBoost **quantile**
bundle — P10 / P50 / P90) and listens on port `5000`. It predicts an occupancy
**rate** from calendar features (hour, day of week, weekend, month, day of year)
plus the Israeli public-holiday flag, then scales the rate by the lot's capacity
(`free = (1 - rate) * totalSlots`), so one model serves a lot of any size. It
exposes two endpoints:

- `POST /predict` — a single-point prediction (used by the `predictAvailability` command).
- `POST /forecast` — a full day (24 hourly points) with a P10–P90 confidence band
  (used by the GUI Predictions tab).

If the ML service is down, the API still responds to `predictAvailability`, but
falls back to the current free-slot count.

### 3. Start The Clients

From the [client repo](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client)
(full from-scratch install steps, including Node via nvm, are in that repo's
README). Requires Node.js 18+:

```sh
cd ../2026b.omer.asayag_client

# Install Node.js 18+ and npm if you don't have them yet. nvm keeps everything in
# your home directory (no sudo, easy version switching):
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
. "$HOME/.nvm/nvm.sh"
nvm install --lts        # installs a current Node.js LTS together with npm
node -v && npm -v        # sanity-check both are on PATH
# apt alternative (needs sudo): sudo apt-get install -y nodejs npm

npm install
npm run dev:mobile   # end-user app  -> http://localhost:8080
npm run dev:admin    # admin console -> http://localhost:8090  (second terminal)
```

Sign in with the seeded users below (e.g. `user@demo.org` / `User@1` in the
mobile app, `admin@demo.org` / `Admin@1` in the admin console).

## Seed Data

The `initDemos` Spring profile is enabled by default in
`src/main/resources/application.properties`. On startup, the API seeds the demo
users and three demo lots (owned by two different admins) if no parking lot
exists.

Seeded users:

| Email | Password | Role | Owns |
|---|---|---|---|
| `admin@demo.org` | `Admin@1` | `ADMIN` | — (Admin API only) |
| `operator@demo.org` | `Oper@1` | `OPERATOR` | North TLV Lot, Central Lot |
| `operator2@demo.org` | `Oper@2` | `OPERATOR` | South Beach Lot |
| `user@demo.org` | `User@1` | `END_USER` | — |

Seeded lots:

| Lot | City | Sections | Slots | Notes |
|---|---|---:|---:|---|
| North TLV Lot | Tel Aviv | 3 (A/B/C) | 33 | A-1..A-3 are handicap; ~61% occupied |
| Central Lot | Tel Aviv | 2 (A/B) | 18 | A-1/A-2 handicap; busy |
| South Beach Lot | Holon | 2 (A/B) | 14 | A-1 handicap; mostly free |

Demo slot health is normalized to `OK` with no heartbeat timestamp. Real slots
that report a heartbeat are still checked by the scheduler and can become
`FAULTY` when stale.

## API Summary

Updated (Sprint 4) operations use `API-Version: 1.4`.

### Roles (Sprint 4)

Every non-user operation authenticates `userSystemID` / `userEmail` /
`userPassword` (creation and commands carry only `userPassword`; identity comes
from `createdBy` / `invokedBy`). Permissions by role:

| Role | May do |
|---|---|
| `ADMIN` | **Only** the Admin API (export users/commands, delete-all). Object reads/writes return 403. |
| `OPERATOR` | Create/update objects and bind relations; read/search **all** objects (active or not). Each lot is owned by the operator who created it. |
| `END_USER` | Read/search **active** objects only (an inactive object is reported as 404, as if absent); the **only** role that may invoke commands. |

Commands additionally require the target object to exist and be active. Wrong
password → 401; wrong role → 403.

### Users

User creation, update and login require no acting identity (an anti-lockout
rule): they are the way accounts come to exist.


| Operation | Method | Path |
|---|---|---|
| Create user | `POST` | `/ambient-invisible-intelligence/users[?actingEmail={adminEmail}]` |
| Login | `GET` | `/ambient-invisible-intelligence/users/login/{systemID}/{userEmail}?password={password}` |
| Update user | `PUT` | `/ambient-invisible-intelligence/users/{systemID}/{userEmail}?password={existingPassword}` |

Creating an `ADMIN` or `OPERATOR` requires `actingEmail` to reference an
existing admin (else HTTP 403); public sign-up creates `END_USER` accounts.

### Objects (Sprint 4 contract)

Every operation authenticates the acting user. Reads/updates carry
`?userSystemID={sys}&userEmail={email}&userPassword={password}`; creation and
commands carry `?userPassword={password}` (identity comes from the boundary's
`createdBy` / `invokedBy`). List operations accept `&size={size}&page={page}`
(default size 20, page 0).

| Operation | Method | Path |
|---|---|---|
| Create object | `POST` | `/objects?userPassword=…` |
| Update object | `PUT` | `/objects/{systemID}/{objectId}?userSystemID=…&userEmail=…&userPassword=…` |
| Delete object (extension) | `DELETE` | `/objects/{systemID}/{objectId}?userSystemID=…&userEmail=…&userPassword=…` |
| Get object | `GET` | `/objects/{systemID}/{objectId}?…credentials…` |
| List objects (paginated) | `GET` | `/objects?…credentials…&size=…&page=…` |
| Bind child | `PUT` | `/objects/{parentSystemID}/{parentObjectId}/children?…credentials…` |
| List children (paginated) | `GET` | `/objects/{parentSystemID}/{parentObjectId}/children?…credentials…&size=…&page=…` |
| List parents (paginated) | `GET` | `/objects/{childSystemID}/{childObjectId}/parents?…credentials…&size=…&page=…` |

Sprint 4 custom search queries (all paginated, all authenticated):

| Search | Path |
|---|---|
| By exact alias | `/objects/search/byAlias/{alias}` |
| By alias pattern (substring) | `/objects/search/byAliasPattern/{pattern}` |
| By type | `/objects/search/byType/{type}` |
| By status | `/objects/search/byStatus/{status}` |
| By location | `/objects/search/byLocation/{lat}/{lng}/{distance}?units=NEUTRAL\|KM\|MILES&shape=SQUARE\|CIRCLE` |

Location search is a square around the point (each edge within `distance`
units); `shape=CIRCLE` is the bonus radius variant. `NEUTRAL` = map degrees,
`KM`/`MILES` are converted (~111.32 km, ~69.17 miles per degree).

**Ownership model:** parking objects (`PARKING_LOT`/`PARKING_SECTION`/`PARKING_SLOT`)
can only be created by an `OPERATOR`, and each lot is controlled by the operator who
created it (`createdBy`). Updating or deleting a parking object requires
`userEmail` to be that owner (else HTTP 403). Deletes cascade: a lot removes its
sections and slots; a section removes its slots.

Object `type` values used by the smart parking domain:

| Type | Main fields |
|---|---|
| `PARKING_LOT` | `objectDetails.city` |
| `PARKING_SECTION` | `objectDetails.parentLotId` |
| `PARKING_SLOT` | `status` is `FREE` or `OCCUPIED`; details include `parentLotId`, `parentSectionId`, `slotKind`, `healthStatus`, optional `lastHeartbeat` |
| `VEHICLE` | details include lot/session fields such as plate, driver, and entry time |

### Commands

Commands are invoked with (the invoker's identity is verified with `userPassword`):

```http
POST /ambient-invisible-intelligence/commands?userPassword={password}
```

Supported commands by target object type:

| Target type | Commands |
|---|---|
| `PARKING_LOT` | `availabilityInLot`, `bestSectionInLot`, `predictAvailability`, `runHealthCheck` |
| `PARKING_SECTION` | `availabilityInSection` |
| `PARKING_SLOT` | `checkSlotAvailability`, `updateSlotState` |
| `VEHICLE` | `vehicleEnters`, `vehicleExits`, `reportSession` |

Useful command attributes:

| Command | Attributes |
|---|---|
| `availabilityInLot` | optional `slotKindFilter`, for example `HANDICAP` |
| `availabilityInSection` | optional `slotKindFilter` |
| `bestSectionInLot` | optional `slotKindFilter` |
| `predictAvailability` | `targetTimestamp` as an ISO-8601 timestamp |
| `updateSlotState` | `newState`, either `FREE` or `OCCUPIED` |
| `reportSession` | `state` |

Example command request:

```json
{
  "command": "availabilityInLot",
  "targetObject": {
    "id": {
      "objectId": "<LOT_ID>",
      "systemID": "2026b.Omer.Asayag"
    }
  },
  "invokedBy": {
    "userId": {
      "email": "user@demo.org",
      "systemID": "2026b.Omer.Asayag"
    }
  },
  "commandAttributes": {}
}
```

### Admin

| Operation | Method | Path |
|---|---|---|
| Delete all users | `DELETE` | `/admin/users?userSystemID=…&userEmail=…&userPassword=…` |
| Delete all objects (incl. relations) | `DELETE` | `/admin/objects?…credentials…` |
| Delete all commands | `DELETE` | `/admin/commands?…credentials…` |
| Export users (paginated) | `GET` | `/admin/users?…credentials…&size=…&page=…` |
| Export commands (paginated) | `GET` | `/admin/commands?…credentials…&size=…&page=…` |

All admin operations require the credentials of an `ADMIN` user.

## GUI Integration

The GUI is a thin client over the API. The paths below are relative to the
[client repo](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client):

- `src/lib/api.ts` defines the API client and helpers (objects, commands,
  users, admin).
- `src/hooks/useParkingData.ts` maps API objects into GUI state and polls
  the live lot, users, and command history.
- `src/components/ParkingLotMap.tsx` renders the live lot.
- `src/components/PredictionsView.tsx` draws a full-day forecast curve with
  a P10–P90 band, fetched from the ML `/forecast` endpoint via the Vite `/ml` proxy.
- `src/pages/Admin.tsx` is the **admin console** at `/admin` (system
  overview, users, command-history activity, and per-lot health checks),
  reached from the shield icon in the app header.

Slot rendering is derived from API data:

| API slot data | GUI state |
|---|---|
| `slotKind=REGULAR`, `status=FREE` | available |
| `slotKind=REGULAR`, `status=OCCUPIED` | occupied |
| `slotKind=HANDICAP`, `status=FREE` | disabled-available |
| `slotKind=HANDICAP`, `status=OCCUPIED` | disabled-occupied |

## Quick Verification

List objects:

```sh
curl -H "API-Version: 1.4" \
  "http://localhost:8084/ambient-invisible-intelligence/objects?userEmail=operator@demo.org&userPassword=Oper@1&size=100"
```

Try a Sprint 4 search query:

```sh
curl -H "API-Version: 1.4" \
  "http://localhost:8084/ambient-invisible-intelligence/objects/search/byType/PARKING_LOT?userEmail=operator@demo.org&userPassword=Oper@1"
```

Check the prediction service directly:

```sh
# single point
curl -X POST http://localhost:5000/predict \
  -H "Content-Type: application/json" \
  -d '{"targetTimestamp":"2026-06-03T12:00:00Z","currentFreeSlots":13,"totalSlots":33}'

# full-day forecast with a P10-P90 band
curl -X POST http://localhost:5000/forecast \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-06-03","totalSlots":33}'
```

Build checks:

```sh
# API (this repo)
./gradlew classes
# GUI (client repo)
cd ../2026b.omer.asayag_client && npm run build
```

Full Java tests use Testcontainers and require Docker:

```sh
./gradlew test
```

For a manual browser/API verification flow, use `LIVE-VERIFICATION.md`.
