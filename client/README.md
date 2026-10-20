# Spot&Slot — Smart Parking clients

The client side of the Smart Parking system (course: *Ambient Invisible
Intelligence*), split into **two apps that share one codebase**:

| App | Audience | Where it runs | Dev | Build output |
|---|---|---|---|---|
| **Mobile** (`mobile/`, `src/apps/mobile`) | Drivers (END_USER) | **Android app** (Capacitor) or mobile browser | `npm run dev:mobile` → :8080 | `dist/mobile` → `android/` |
| **Admin** (`admin/`, `src/apps/admin`) | Lot administrators | Desktop browser | `npm run dev:admin` → :8090 | `dist/admin` |

Both are Vite + React + TypeScript + shadcn/ui + Tailwind + TanStack Query and
share `src/` (API client, auth, hooks, UI components). Every number shown comes
from the Smart Parking REST API — no mock data.

## Mobile app (drivers)

- Sign-in / sign-up (public sign-up creates `END_USER` accounts), Hebrew RTL.
- **Lot selector** — pick which parking lot to view.
- **Live map** — sections and slots in real time (~5 s polling), handicap spots,
  occupancy %, "notify me when a spot frees up".
- **Predictions** — full-day ML forecast with a P10–P90 confidence band.
- **Server address** — on a phone, `localhost` is the phone; the login screen has
  a collapsible "כתובת שרת" field to point at the API
  (e.g. `http://192.168.1.20:8084/ambient-invisible-intelligence`). Persisted.

### Build the Android app (APK)

Prerequisites: a JDK (17–21) and the Android SDK. Installing **Android Studio**
provides both; on a headless Linux machine the command-line SDK tools suffice.

```sh
npm install                # first time only
npm run cap:sync           # builds dist/mobile and syncs it into android/
cd android
```

Then build the debug APK:

```sh
# Windows (Android Studio installed):
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug

# Linux (Android Studio installed):
JAVA_HOME="$HOME/android-studio/jbr" ./gradlew assembleDebug

# Linux (no Android Studio — JDK + cmdline SDK tools only):
sudo apt-get install -y openjdk-21-jdk unzip
# download "command line tools" from developer.android.com/studio, unzip to $HOME/android-sdk/cmdline-tools/latest
export ANDROID_HOME="$HOME/android-sdk"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
./gradlew assembleDebug

# → android/app/build/outputs/apk/debug/app-debug.apk
```

Install `app-debug.apk` on a device/emulator (`adb install app-debug.apk`), or
open `android/` in Android Studio and press Run. If Gradle can't find the SDK,
create `android/local.properties` containing one line —
`sdk.dir=/home/<you>/Android/Sdk` (Linux) or
`sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (Windows, forward slashes).

## Staff console (desktop)

Staff (ADMIN or OPERATOR) sign in here; drivers (END_USER) are turned away and
use the mobile app. The sidebar adapts to the role (Sprint 4 role model):

- **OPERATOR** — **Dashboard** (stats + per-lot occupancy) and **My Lots**
  (create/rename/delete lots, manage sections & slots). Scoped by ownership:
  each operator sees and manages only the lots they created.
- **ADMIN** — **Users** (list + create users of any role) and **Activity**
  (command history). Per the role model, ADMINs use only the Admin API and do
  not read parking objects.

Health checks are commands, which only END_USERs may invoke, so they are not
offered in the console.

## How the apps talk to the backend

| Concern | File |
|---|---|
| API base URL, system id | `.env` (see `.env.example`) + runtime override (`getServerBase`) |
| Typed API client (objects, commands, users, admin, auth) | `src/lib/api.ts` |
| Signed-in user state | `src/lib/auth.tsx` |
| Selected lot state (mobile) | `src/lib/selectedLot.tsx` |
| Data fetching, polling, slot mapping | `src/hooks/useParkingData.ts` |
| Admin data/stat scoping + mutations | `src/apps/admin/hooks.ts` |

- **API calls** go to the Spring server (`/ambient-invisible-intelligence`,
  default `http://localhost:8084`).
- **The ML forecast**: in web dev the Vite proxy forwards `/ml` → `:5000`
  (`vite.config.mobile.ts`); the native Android app calls the ML service
  directly on the API server's host (`mlUrl()` in `src/lib/api.ts`).

Seeded demo users (server `initDemos` profile):

| Email | Password | Role | Owns |
|---|---|---|---|
| `admin@demo.org` | `Admin@1` | ADMIN | — (Admin API only) |
| `operator@demo.org` | `Oper@1` | OPERATOR | North TLV Lot, Central Lot |
| `operator2@demo.org` | `Oper@2` | OPERATOR | South Beach Lot |
| `user@demo.org` | `User@1` | END_USER | — (drivers use the mobile app) |

## Run (web dev)

### 0. One-time machine setup (clean Linux — no Node assumed)

Both apps require **Node.js 18 or newer**. Distribution packages are often too
old, so install via `nvm` (works on any distro, no sudo needed):

```sh
sudo apt-get update && sudo apt-get install -y git curl   # Ubuntu/Debian
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
. "$HOME/.nvm/nvm.sh"          # or open a new terminal
nvm install --lts              # installs Node 22 LTS + npm
node -v && npm -v              # verify: node 18+ and npm print versions
```

> Already have Node? `node -v` must print v18 or higher — Vite 5 refuses to
> run on older versions.

### 1. Install dependencies and start

From this repository's root:

```sh
npm install          # first time only (downloads all packages; needs internet)
npm run dev:mobile   # driver app  → http://localhost:8080
npm run dev:admin    # admin app   → http://localhost:8090  (run in a second terminal)
```

Both dev servers bind to all interfaces, so they are also reachable from other
devices on the network via this machine's IP.

Prerequisites for full functionality (see the API repo README for their
from-scratch setup):

- the **Smart Parking API** on `http://localhost:8084`
- the **ML prediction service** on `http://localhost:5000` (Predictions tab;
  everything else works without it)

Other scripts: `npm run build` (both apps), `npm run build:mobile`,
`npm run build:admin`, `npm run cap:sync`, `npm test` (unit tests),
`npm run lint`.

## Notes

- If the API is unreachable, both apps show a clear "no connection" state.
- The slot grid maps API data as: `REGULAR/FREE → available`,
  `REGULAR/OCCUPIED → occupied`, `HANDICAP/FREE → disabled-available`,
  `HANDICAP/OCCUPIED → disabled-occupied`.
- The Android WebView allows cleartext HTTP (demo convenience —
  `capacitor.config.ts`); the API's CORS config accepts the Capacitor origins
  and LAN addresses.
