# API Performance Test — Apache JMeter

Load test for the Spot&Slot Smart Parking API (Sprint 4, API `v1.4`).

File: [`smart-parking-perf.jmx`](smart-parking-perf.jmx)

## What it exercises

A **setUp** phase queries `/objects` (as an operator) and extracts a real
`PARKING_LOT` id into a global property (`LOT_ID`). The main **Load** thread group
then repeatedly hits, per virtual user, with think time between requests:

| # | Request | Role | Notes |
|---|---------|------|-------|
| R1 | `GET /objects?size=20&page=0` | OPERATOR | paged list |
| R2 | `GET /objects/search/byType/PARKING_SLOT` | OPERATOR | search by type |
| R3 | `GET /objects/search/byStatus/FREE` | END_USER | active-only search |
| R4 | `GET /objects/search/byLocation/32.0853/34.7818/5?units=KM` | END_USER | geo search |
| C1 | `POST /commands` (`availabilityInLot` on `LOT_ID`) | END_USER | domain command |

Every sample must return **HTTP 200** (Response Assertion). All requests send the
`API-Version: 1.4` header. Results feed a **Summary Report** and **Aggregate Report**.

## Prerequisites

1. **Java 17+** (needed by both the server and JMeter).
2. **Apache JMeter 5.6.x**.
   - Linux (bare VM):
     ```bash
     sudo apt-get update && sudo apt-get install -y default-jre unzip curl
     curl -fsSLO https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.3.tgz
     tar -xzf apache-jmeter-5.6.3.tgz
     export PATH="$PWD/apache-jmeter-5.6.3/bin:$PATH"
     jmeter --version
     ```
   - Windows: download the `.zip` from https://jmeter.apache.org/download_jmeter.cgi, unzip,
     and use `bin\jmeter.bat`.
3. **The API running with seed data.** The default profile seeds 3 lots + sections + slots,
   which the test needs:
   ```bash
   # from the repo root
   ./gradlew bootRun            # Linux/macOS
   gradlew.bat bootRun          # Windows
   ```
   The server listens on `http://localhost:8084/ambient-invisible-intelligence`.
   > Do **not** use the `prod` profile for this test — it disables seeding, so there is no
   > lot to target and `C1` would return 404.

The test uses the seeded credentials (system id `2026b.Omer.Asayag`):
`operator@demo.org` / `Oper@1` and `user@demo.org` / `User@1`. Change them with `-J` flags if needed.

## Run it — headless (recommended for load)

```bash
jmeter -n -t perf/smart-parking-perf.jmx \
       -l perf/results/results.jtl \
       -e -o perf/results/html
```

- `-n` non-GUI, `-t` the plan, `-l` raw results (JTL), `-e -o` generate an HTML dashboard.
- Open `perf/results/html/index.html` for graphs (throughput, response times, percentiles).
- `perf/results/` must not already exist when using `-e -o` (JMeter refuses to overwrite);
  delete it between runs.

## Run it — GUI (for editing / debugging)

```bash
jmeter -t perf/smart-parking-perf.jmx
```
Enable the disabled **View Results Tree** listener to inspect individual responses.

## Tuning (override any default with `-J`)

| Property | Default | Meaning |
|----------|---------|---------|
| `threads` | 20 | concurrent virtual users |
| `rampup` | 15 | seconds to start all users |
| `loops` | 30 | iterations per user (each = the 5 requests above) |
| `host` | localhost | server host (use the LAN IP to hit a remote VM) |
| `port` | 8084 | server port |
| `protocol` | http | http/https |
| `basepath` | /ambient-invisible-intelligence | API base path |
| `sys` | 2026b.Omer.Asayag | system id |
| `opEmail` / `opPw` | operator@demo.org / Oper@1 | OPERATOR credentials |
| `euEmail` / `euPw` | user@demo.org / User@1 | END_USER credentials |

Example — a heavier run against a VM:
```bash
jmeter -n -t perf/smart-parking-perf.jmx -l perf/results/results.jtl -e -o perf/results/html \
       -Jhost=192.168.1.20 -Jthreads=100 -Jrampup=30 -Jloops=200
```

Default load = 20 users × 30 loops × 5 requests ≈ **3,000 requests**.

## Reading the results

In the Aggregate/Summary report or the HTML dashboard, focus on:
- **Average / Median / 90%–95%–99% line** — latency distribution.
- **Throughput** — requests/sec the API sustained.
- **Error %** — should be **0%**. Non-zero usually means the server isn't running,
  it lacks seed data, or the credentials/host were overridden incorrectly.
