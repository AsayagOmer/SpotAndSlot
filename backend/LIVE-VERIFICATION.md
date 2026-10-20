# Live Verification Handoff — Smart Parking + Spot-Insight GUI

**Purpose:** verify, by a live click-through, that the Spot-Insight GUI (the
[client repo](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client)) correctly
represents the data and behavior of the Smart Parking API (this repo's Spring Boot
server). This document is self-contained — no prior conversation needed.

> The system is split across three repos: this API repo, the
> [client repo](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_client), and the
> [prediction-service repo](https://bitbucket.org/tomcatafeka/2026b.omer.asayag_predictive_service).
> Paths written as `client/...` are relative to the client repo and
> `prediction-service/...` to the prediction-service repo; the steps assume all
> three are cloned side by side as sibling directories.

Read alongside:
- `INTEGRATION.md` — architecture, data mapping, run instructions
- `docs/SRS draft.docx` — use cases and non-functional requirements
- `docs/Test-Report-Smart-Parking.docx` — 26 API test scenarios (GIVEN/WHEN/THEN)
- `docs/Model-Evaluation-Report.docx` — the ML model study

---

## 0. What "correct" means here

The GUI is a **thin client**: every number and tile it shows must come from the API,
not from mock data. The check is essentially: *does what the browser shows match what
`GET /objects` returns?*

Contract facts (verify these are what the running server actually uses):
- Base URL: `http://localhost:8084/ambient-invisible-intelligence`
- System ID: `2026b.Omer.Asayag`
- API version header: `API-Version: 1.3`
- GUI dev server: `http://localhost:8080`
- GUI config lives in `client/.env` (falls back to the same values in `client/src/lib/api.ts`)

---

## 1. Start everything

```sh
# 1) API (root) — no DB installation needed (in-memory H2; requires JDK 17+ only).
#    With Docker installed you may instead run plain `./gradlew bootRun` (Postgres via compose.yaml).
./gradlew bootRun --args="--spring.docker.compose.enabled=false"

# 2) (optional) ML prediction microservice on :5000 (see its README for python3/pip/venv setup)
cd ../2026b.omer.asayag_predictive_service
python3 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt   # first time only
python app.py

# 3) Clients (Node.js 18+ — see the client repo README for nvm setup)
cd ../2026b.omer.asayag_client
npm install
npm run dev:mobile   # end-user app  -> http://localhost:8080
npm run dev:admin    # admin console -> http://localhost:8090  (second terminal)
```

The server seeds demo data on boot (profile `initDemos`): **3 lots owned by two
different admins**. The table below describes the largest one, **North TLV Lot**
(select it in the app's lot picker):

---

## 2. Expected seed state (fresh DB)

| Section | Slots | Occupied | Notes |
|---|---|---|---|
| A | 11 | 7 | A‑1, A‑2, A‑3 are **HANDICAP** (A‑1 free, A‑2 occupied, A‑3 free) |
| B | 10 | 7 | all regular |
| C | 12 | 6 | all regular |
| **Total** | **33** | **20** | **free = 13**, occupancy = 20/33 ≈ **61%** |

> Note: the seeder is idempotent (only seeds when no lot exists). In the optional
> Docker/Postgres mode, a volume with data from earlier runs means numbers may
> differ — derive expected values from the API (step 4), not from this table.

---

## 3. Checklist — what to verify in the browser

**A. Live tab (default)**
- [ ] Lot name shown matches the lot's `alias` from the API (seed: "Demo Lot").
- [ ] Three section blocks (A, B, C) with 11 / 10 / 12 tiles = 33 total.
- [ ] Handicap tiles render with the accessibility icon (3 of them, in section A).
- [ ] Occupancy badge matches the API count (see step 4). Seed ≈ 61%.
- [ ] "Last updated" indicator shows a recent time and a live dot.

**B. Predictions tab** (requires the ML service on :5000)
- [ ] A full-day forecast curve renders with a shaded **P10–P90 confidence band**.
- [ ] Changing the **date** changes the curve (week-to-week variation — it is not identical every week).
- [ ] A Jewish holiday (e.g. 2024-10-12, Yom Kippur) shows a holiday banner and a near-empty forecast.
- [ ] Date and time pickers work and don't crash the page.

**C. Live polling (the key proof the GUI is not mocked)**
- [ ] With the live tab open, change one slot on the server (step 5).
- [ ] Within ~5 seconds the GUI updates: the occupancy % changes and the specific
      tile flips color, with no manual refresh.

**D. Error handling**
- [ ] Stop the API; the GUI shows a "no connection to server" state with a retry button.
- [ ] Restart the API and click retry; live data returns.

**E. Network / CORS (DevTools)**
- [ ] No CORS errors in the console.
- [ ] Network tab shows `GET …/objects` returning HTTP 200 with a JSON array, repeating ≈ every 5s.

**F. Admin console (`/admin`, shield icon in the header)**
- [ ] Stat cards (lots / sections / slots / free / vehicle events / faulty) match the API.
- [ ] Users tab lists the seeded users with role badges (ADMIN, OPERATOR).
- [ ] Activity tab shows the command history, newest first, with targets resolved to type + alias.
- [ ] "Run health check" on a lot returns a result (and is recorded in Activity); marking a
      slot FAULTY via the API makes the faulty count turn red within one poll.

**G. Cross-check against the written docs**
- [ ] Pick 3–4 scenarios from `docs/Test-Report-Smart-Parking.docx` and run them against
      the live API (curl/Postman); confirm the server matches the documented THEN.
- [ ] Confirm the GUI exercises the SRS use cases it claims to (Check Lot Availability →
      live tab; Predict Parking Availability → predictions tab; View System Dashboard → /admin).

---

## 4. How to compute the expected occupancy from the API

```sh
curl -s -H "API-Version: 1.3" "http://localhost:8084/ambient-invisible-intelligence/objects?userEmail=admin@demo.org&userPassword=Admin@1&size=200"
```
Filter `type == "PARKING_SLOT"`, count `status == "OCCUPIED"` over the total, round to a
percentage, and compare to the GUI badge. They must match.

**Data mapping the GUI uses** (`objectDetails.slotKind` × `status`):

| slotKind | status | GUI tile |
|---|---|---|
| REGULAR | FREE | available (green) |
| REGULAR | OCCUPIED | occupied (gray, ✕) |
| HANDICAP | FREE | disabled‑available (green + ♿) |
| HANDICAP | OCCUPIED | disabled‑occupied (gray + ♿) |

Slots are grouped by `objectDetails.parentSectionId`; sections by `parentLotId`;
the GUI displays the **lot chosen in the lot picker** (first lot by default).

---

## 5. How to flip a slot for the polling test

```sh
# a) find a FREE slot's objectId
curl -s -H "API-Version: 1.3" "http://localhost:8084/ambient-invisible-intelligence/objects?userEmail=admin@demo.org&userPassword=Admin@1&size=200" \
  | grep -o '"objectId":"[^"]*"... '   # or use jq to find a PARKING_SLOT with status FREE

# b) occupy it via the updateSlotState command (targets a PARKING_SLOT)
curl -s -X POST -H "API-Version: 1.3" -H "Content-Type: application/json" \
  -d '{
        "command":"updateSlotState",
        "targetObject":{"id":{"objectId":"<SLOT_ID>","systemID":"2026b.Omer.Asayag"}},
        "invokedBy":{"userId":{"email":"admin@demo.org","systemID":"2026b.Omer.Asayag"}},
        "commandAttributes":{"newState":"OCCUPIED"}
      }' \
  "http://localhost:8084/ambient-invisible-intelligence/commands?userPassword=Admin@1"
```
Then watch the live tab update within ~5s. `admin@demo.org` is a seeded user; commands
require a non-blank `invokedBy.email` + `systemID` (HTTP 400 otherwise) and the
invoker's password in the `userPassword` parameter (HTTP 401 otherwise, Sprint 4).

---

## 6. Where the integration lives (for tracing issues)

| Concern | File |
|---|---|
| API base URL / identity | `client/.env` |
| Typed API client | `client/src/lib/api.ts` |
| Fetch + mapping + polling | `client/src/hooks/useParkingData.ts` |
| Live map view | `client/src/components/ParkingLotMap.tsx` |
| Predictions view (forecast + band) | `client/src/components/PredictionsView.tsx` |
| Admin console | `client/src/pages/Admin.tsx` (route `/admin`) |
| ML forecast (proxy + endpoint) | `client/vite.config.ts` (`/ml`) + `prediction-service/app.py` (`/forecast`) |
| CORS (server allows :8080) | `src/main/java/ambient_invisible_intelligence/config/WebConfig.java` |
| Seed data | `src/main/java/ambient_invisible_intelligence/DataInitializer.java` |

---

## 7. Known caveats to validate

- The full flow — API + database + ML service + clients, including the live Java → Python
  prediction chain and the admin console — has been verified end to end; use this
  checklist to re-verify after changes.
- With the default in-memory H2 database, **all data resets on every API restart**
  and the seed state in step 2 reappears. With the optional Docker/Postgres mode,
  data persists between restarts — if counts look stale there, drop the Postgres
  container/volume and reboot to get the clean seed state.
