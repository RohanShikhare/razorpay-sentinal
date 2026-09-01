# Razorpay Sentinel

**AI Payment Incident Commander**

Sentinel watches Razorpay payment traffic for abnormal failure patterns,
diagnoses why they're happening, quantifies the revenue at risk, and proposes
a bounded recovery strategy — one that a deterministic policy engine has to
approve before anything is allowed to run.

> **Status: Module 3 — Monitoring & Incident Detection Engine.** The
> foundation, a realistic synthetic payment dataset, a deterministic
> incident simulator, and a deterministic detection engine are implemented.
> AI diagnosis, policy gating, and recovery execution described below are
> **not implemented yet**. See [Current implementation status](#current-implementation-status).

## Core workflow

```
Detect → Diagnose → Quantify → Decide → Gate → Recover → Audit
```

| Stage | What it does |
|-------|--------------|
| Detect | Deterministic detection of abnormal payment failure patterns |
| Diagnose | AI root-cause analysis of a detected incident |
| Quantify | Calculates revenue at risk |
| Decide | AI recommends a bounded recovery strategy |
| Gate | A deterministic policy engine validates the recommendation |
| Recover | The approved strategy is executed (simulated) |
| Audit | Every step is recorded to a complete audit trail |

## Technology stack

**Backend** — Java 17, Spring Boot 3, Maven, Spring Web, Spring Data JPA,
PostgreSQL, Bean Validation.

**Frontend** — Next.js 14 (App Router), React, TypeScript, Tailwind CSS,
Recharts (charts on the Monitoring and Incident Detail pages).

**Database** — PostgreSQL, configured entirely through environment
variables.

**Architecture** — a single monolith. No microservices, no message queue, no
Docker in this module.

## Current implementation status

### Implemented so far

**Module 0 — Project Foundation**

- Spring Boot backend skeleton with a layered `controller → service →
  repository` package structure.
- `GET /api/v1/health` — confirms the backend is running.
- Environment-variable based PostgreSQL configuration.
- Global exception handling and a validation foundation (`SentinelException`,
  `GlobalExceptionHandler`).
- CORS configured for a local Next.js dev server.
- Next.js + TypeScript + Tailwind frontend shell: homepage, navigation, a
  live backend-connectivity panel.

**Module 1 — Payment Data Engine**

- `Payment` JPA entity with `PaymentMethod`, `PaymentStatus`, `ErrorCode`
  enums and a `Banks` constants holder — persisted to a `payments` table.
- Weighted, realistic synthetic payment generation (`PaymentGeneratorService`):
  configurable time window, seeded-random reproducibility, no forced error
  codes on non-failed payments, no single bank or error code dominating.
- `POST /api/v1/payments/generate` — generate and persist synthetic payments.
- `GET /api/v1/payments` — paginated, filterable payment listing.
- `GET /api/v1/payments/summary` — database-aggregated totals, rates, and
  breakdowns by payment method, bank, and error code.
- A `/payments` page in the frontend: generate data, filter, and page
  through results.
- Unit tests for the generator's statistical properties, plus an H2-backed
  integration test suite covering generation, persistence, filtering,
  pagination, and summary math (see [Build verification notes](#build-verification-notes)).

**Module 2 — Incident Simulator**

- `SimulationScenario` enum plus lightweight `SimulationRun`/`SimulationChange`
  simulation records — explicitly *not* an "Incident" entity (see
  [Incident Simulator](#incident-simulator) below).
- `IncidentSimulationService` — deterministically degrades the dataset via
  three reproducible scenarios: `BANK_X_UPI_DEGRADATION` (primary demo),
  `MERCHANT_SPECIFIC_DEGRADATION`, and `NORMAL_RANDOM_FAILURES`.
- `POST /api/v1/simulations/run`, `POST /api/v1/simulations/reset`,
  `GET /api/v1/simulations/status`.
- Reset restores affected payments to their pre-simulation values without
  regenerating the dataset. Starting a new simulation while one is active
  automatically resets the previous one first, so repeated runs never compound.
- A `/simulator` page in the frontend with a scenario selector, Simulate/Reset
  controls, and a HEALTHY / SIMULATION ACTIVE status panel. The `/payments`
  page shows a small indicator when a simulation is active.
- Integration tests covering scenario concentration, merchant isolation,
  normal-noise non-concentration, reset, and idempotency under repeated runs.

**Module 3 — Monitoring & Incident Detection Engine**

- `Incident` entity plus `IncidentType`, `IncidentSeverity`, `IncidentStatus`
  enums. `rootCause` stays null — assigning one is a future AI module's job
  (see [Monitoring & Incident Detection Engine](#monitoring--incident-detection-engine) below).
- `PaymentStatistics` / `SegmentAnalyzer` — pure statistics helpers comparing
  baseline vs. current-window failure rates, globally and per bank / payment
  method / bank+method / merchant.
- `IncidentDetectionService` — adaptive window widening, segment-level
  detection, configurable severity thresholds, a documented revenue-at-risk
  formula, a deterministic confidence score, and duplicate suppression.
- `POST /api/v1/monitoring/detect`, `GET /api/v1/monitoring/summary`,
  `GET /api/v1/incidents`, `GET /api/v1/incidents/{id}`.
- `/monitoring` (command center) and `/incidents` + `/incidents/[id]` pages
  in the frontend, with Recharts visualizations.
- Unit tests for the statistics/segment helpers, plus an H2-backed
  integration suite covering all three simulator scenarios, duplicate
  suppression, pagination, and the monitoring summary.
- A refinement to Module 2's simulator: degradation is now concentrated in
  recent transactions instead of spread uniformly across the whole 48h
  span — both more realistic and necessary for this module's time-window
  comparison to actually see it (details in `docs/architecture.md`).

### Not implemented yet

- Authentication (intentionally out of scope for this internal tool for now).
- AI root-cause diagnosis and recovery recommendation (the detector never
  guesses `rootCause` — see
  [Monitoring & Incident Detection Engine](#monitoring--incident-detection-engine)).
- Policy/guardrail validation.
- Recovery execution (simulated), audit trail, and evaluation.
- Incident lifecycle beyond `DETECTED` (no resolution workflow yet).

See `docs/development-roadmap.md` for the full 16-module plan.

## Project structure

```
razorpay-sentinel/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/sentinelpulse/
│       │   │   ├── RazorpaySentinelApplication.java
│       │   │   ├── config/         # CORS, monitoring thresholds/config
│       │   │   ├── controller/     # HTTP endpoints only
│       │   │   ├── dto/            # request/response shapes
│       │   │   ├── entity/         # Payment, Simulation*, Incident + enums (Modules 1-3)
│       │   │   ├── exception/      # SentinelException + global handler
│       │   │   ├── repository/     # PaymentRepository, Simulation*Repository, IncidentRepository
│       │   │   ├── service/        # business logic (payments, simulation)
│       │   │   ├── monitoring/     # deterministic detection engine (Module 3)
│       │   │   ├── recovery/       # recovery execution (empty — future modules)
│       │   │   ├── ai/             # AI diagnosis/recommendation (empty — future modules)
│       │   │   ├── policy/         # guardrail validation (empty — future modules)
│       │   │   └── audit/          # audit trail (empty — future modules)
│       │   └── resources/application.yml
│       └── test/
│           ├── java/com/sentinelpulse/    # unit + integration tests
│           └── resources/application.yml # test-only H2 datasource override
├── frontend/
│   ├── package.json
│   └── src/
│       ├── app/                # Next.js App Router pages (/, /payments, /simulator, /monitoring, /incidents, /incidents/[id])
│       ├── components/         # UI components (payments/, simulator/, monitoring/)
│       └── lib/                # api.ts, payments.ts, simulations.ts, monitoring.ts, config.ts, navigation.ts
├── docs/
│   ├── architecture.md
│   └── development-roadmap.md
├── .gitignore
└── README.md
```

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Node.js 18+** and npm
- **PostgreSQL 14+** (only needed once a module adds database entities —
  Module 0 does not require it to start)

## Environment variables

### Backend

Set these before running the backend against a real database. Sensible
defaults are provided so the app starts without any setup for local
development against a local Postgres install on default settings.

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/razorpay_sentinel` | JDBC connection URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `SENTINEL_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated list of allowed frontend origins |
| `SENTINEL_PAYMENT_GENERATION_WINDOW_HOURS` | `48` | How far back synthetic payment timestamps are spread |
| `SENTINEL_MONITORING_DEFAULT_WINDOW_MINUTES` | `60` | Default "current" monitoring window size |
| `SENTINEL_MONITORING_MIN_SAMPLE_SIZE` | `100` | Minimum transactions required in the current window before the adaptive widening stops |
| `SENTINEL_MONITORING_MIN_SEGMENT_SAMPLE_SIZE` | `15` | Minimum combined baseline+current volume for a segment to be evaluated |
| `SENTINEL_MONITORING_MAX_WINDOW_MINUTES` | `2880` | Cap on adaptive window widening (default: 48h) |
| `SENTINEL_MONITORING_THRESHOLD_MINIMUM` | `5.0` | Percentage-point increase below which nothing is flagged |
| `SENTINEL_MONITORING_THRESHOLD_LOW` / `_MEDIUM` / `_HIGH` / `_CRITICAL` | `5.0` / `12.0` / `20.0` / `30.0` | Severity band boundaries (percentage points) |

Example (macOS/Linux shell):

```bash
export DB_URL="jdbc:postgresql://localhost:5432/razorpay_sentinel"
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"
```

No database needs to exist yet to start the backend in Module 0 — there are
no JPA entities, so Hibernate has nothing to validate against on startup.
Create the `razorpay_sentinel` database before Module 2 (Payment Data
Engine) adds entities.

### Frontend

Copy `frontend/.env.example` to `frontend/.env.local` and adjust if needed:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

## How to run the backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on `http://localhost:8080` by default.

## How to run the frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:3000` by default and calls the
backend health endpoint on load.

## Health-check endpoint

```
GET /api/v1/health
```

Example response:

```json
{
  "status": "UP",
  "service": "razorpay-sentinel",
  "module": "module-0-foundation",
  "timestamp": "2026-09-01T12:00:00Z"
}
```

## Payment Data Engine API

### Generate payments

```
POST /api/v1/payments/generate
```

Generates and persists synthetic payments. `count` must be between 1 and
10,000. `seed` is optional — provide it for reproducible generation, omit
it for fresh randomness each call.

Request:

```json
{
  "count": 1000
}
```

Response:

```json
{
  "requestedCount": 1000,
  "generatedCount": 1000,
  "windowStart": "2026-08-30T12:00:00Z",
  "windowEnd": "2026-09-01T12:00:00Z",
  "generatedAt": "2026-09-01T12:00:00Z"
}
```

### List payments

```
GET /api/v1/payments
```

Standard Spring pagination: `page` (0-indexed, default `0`), `size`
(default `20`, capped at `100`), plus optional `sort`.

Optional filters, any combination of:

| Param | Example |
|---|---|
| `status` | `SUCCESS`, `FAILED`, `PENDING` |
| `paymentMethod` | `UPI`, `CARD`, `NETBANKING`, `WALLET` |
| `bank` | `Bank A`, `Bank B`, `Bank C`, `Bank X`, `Bank Y` |
| `errorCode` | `BANK_TIMEOUT`, `INSUFFICIENT_FUNDS`, `NETWORK_ERROR`, `ISSUER_DECLINED`, `RATE_LIMIT`, `UNKNOWN` |
| `from` / `to` | ISO-8601 instants, bound the transaction timestamp |

Examples:

```
GET /api/v1/payments?page=0&size=20
GET /api/v1/payments?status=FAILED
GET /api/v1/payments?bank=Bank%20X&paymentMethod=UPI
```

Response is a standard Spring `Page<Payment>` body (`content`,
`totalElements`, `totalPages`, `number`, `size`, ...).

### Summary

```
GET /api/v1/payments/summary
```

Returns aggregate totals (computed via database aggregation, not by loading
every row): `totalTransactions`, `successfulTransactions`,
`failedTransactions`, `pendingTransactions`, `totalTransactionValue`,
`successfulTransactionValue`, `failedTransactionValue`, `successRate`,
`failureRate`, plus breakdowns `byPaymentMethod`, `byBank`, and
`byErrorCode`.

## Incident Simulator

### Why it exists

Later modules (root-cause analysis, recovery) need a real incident to work
with, but we can't wait for production traffic to misbehave on demand. The
Incident Simulator deliberately degrades the synthetic payment dataset in
controlled, reproducible ways so those modules — and the future Monitoring
& Incident Detection Engine — have something concrete to detect, diagnose,
and recover from.

**The simulator creates conditions in the data. It never declares that an
incident has been detected.** That judgment belongs to the Monitoring &
Incident Detection Engine (planned for a later module), which will read the
same `payments` table the simulator modifies — regardless of whether the
abnormal state came from the simulator or from real traffic. See
`docs/architecture.md` for the full explanation of this boundary.

### Available scenarios

| Scenario | What it does |
|---|---|
| `BANK_X_UPI_DEGRADATION` | **Primary demo scenario.** Bank X's UPI traffic develops a sharply elevated failure rate (roughly 25–40%, exact value depends on the dataset), dominated by `BANK_TIMEOUT`. Every other bank/payment-method combination is left alone. |
| `MERCHANT_SPECIFIC_DEGRADATION` | One auto-selected, high-volume merchant develops an elevated failure rate with a non-bank-flavored error mix (`ISSUER_DECLINED`, `INSUFFICIENT_FUNDS`, ...). Other merchants stay normal — designed to test whether later root-cause analysis wrongly blames a bank. |
| `NORMAL_RANDOM_FAILURES` | A small, broadly-spread increase in failures across the whole dataset — ordinary operational noise, not concentrated on any one segment. A negative/control case for future incident detection. |

### API endpoints

**Run a scenario**

```
POST /api/v1/simulations/run
```

```json
{ "scenario": "BANK_X_UPI_DEGRADATION" }
```

Response includes `affectedTransactions`, `modifiedTransactions`,
`targetBank`/`targetPaymentMethod`/`targetMerchantId` (whichever apply),
`failureRateBefore`, `failureRateAfter`, `simulationTimestamp`, and
`previousSimulationReset` (true if an already-active simulation was reset
before this one was applied — starting a new simulation while one is
active always does this automatically, so repeated runs never compound).

**Check status**

```
GET /api/v1/simulations/status
```

Returns `active: false` if nothing is running, or the active run's scenario,
start time, and current metrics if one is.

**Reset**

```
POST /api/v1/simulations/reset
```

Restores every payment the active simulation touched to its pre-simulation
`status`/`errorCode` — no dataset regeneration needed — and marks the
simulation inactive. Safe to call even if nothing is active (returns
`reset: false`).

### Running the primary demo scenario

```bash
curl -X POST localhost:8080/api/v1/payments/generate -H 'Content-Type: application/json' -d '{"count":1000}'
curl -X POST localhost:8080/api/v1/simulations/run -H 'Content-Type: application/json' -d '{"scenario":"BANK_X_UPI_DEGRADATION"}'
curl localhost:8080/api/v1/simulations/status
curl -X POST localhost:8080/api/v1/simulations/reset
```

Or use the `/simulator` page in the frontend.

## Monitoring & Incident Detection Engine

**The current engine detects abnormal payment behavior deterministically.
Root-cause diagnosis is intentionally separated and will be handled by the
AI investigation module.** See `docs/architecture.md` for the full design
writeup (baseline calculation, adaptive window widening, segment detection,
severity thresholds, the revenue-at-risk formula, and the confidence score) —
this section covers the APIs and how to run the primary demo end-to-end.

### Run detection

```
POST /api/v1/monitoring/detect
```

Optional body (omit for the default window):

```json
{ "windowMinutes": 60 }
```

Returns whether an incident was detected, its type/severity if so, the
baseline/current failure rates and the increase between them, affected
transactions and value, revenue at risk, a deterministic confidence score,
the effective monitoring window used, and up to 8 top affected segments. If
no incident is detected, `incidentDetected: false` and `message` explains why
— this is a normal 200 response, not an error.

### Monitoring summary

```
GET /api/v1/monitoring/summary
```

Current health at a glance: `paymentHealth` (`NORMAL` or the highest
severity among active incidents), success/failure rates, baseline failure
rate and the change from it, total revenue at risk, and active incident /
affected transaction counts across all currently-active incidents.

### Incidents

```
GET /api/v1/incidents            # paginated list, most recent first
GET /api/v1/incidents/{id}       # detail, with live-recomputed top segments
```

No update endpoints yet — `rootCause` assignment and lifecycle transitions
(`INVESTIGATING`, `RECOVERY_PENDING`, ...) belong to later modules.

### Running the primary demo scenario end-to-end

```bash
curl -X POST localhost:8080/api/v1/payments/generate -H 'Content-Type: application/json' -d '{"count":3000}'
curl -X POST localhost:8080/api/v1/simulations/run -H 'Content-Type: application/json' -d '{"scenario":"BANK_X_UPI_DEGRADATION"}'
curl -X POST localhost:8080/api/v1/monitoring/detect -H 'Content-Type: application/json' -d '{}'
```

Or use the `/simulator` page to trigger the scenario and the `/monitoring`
page's "Run Detection" button in the frontend.

## Build verification notes

- **Frontend**: `npm install` and `npm run build` were run against this
  exact project (including the new `/monitoring`, `/incidents`, and
  `/incidents/[id]` pages and the Recharts dependency) and completed
  successfully with no TypeScript or lint errors — all routes generated.
- **Backend**: the Module 3 code (`Incident` entity, `PaymentStatistics`,
  `SegmentAnalyzer`, `IncidentDetectionService`, controllers, tests) was
  written and carefully reviewed against standard Spring Boot 3 / Java 17 /
  Spring Data JPA patterns. `mvn compile` / `mvn test` could not actually be
  executed end-to-end in the environment this project was built in, because
  that environment's network access does not include Maven Central
  (`repo.maven.apache.org`) — confirmed again this module (the request was
  rejected at the network layer). One real bug *was* caught locally despite
  this: an unescaped `&` in `pom.xml`'s description broke Maven's XML
  parsing; it's fixed, and `mvn -q compile` now gets past POM parsing and
  fails only on the expected dependency-download network block, not on
  malformed XML. Run `mvn clean test` locally to fetch dependencies and
  confirm both the build and the full test suite (Module 0 through Module 3)
  before relying on them. Tests run against an in-memory H2 database (test
  scope only — see `pom.xml`), so `mvn test` does not require a live
  PostgreSQL instance; running the app itself still does.
- **A note on Module 2's simulator**: while building this module, comparing
  a recent "current window" against an older "baseline" only works if
  simulated degradation actually shows up more in recent transactions than
  old ones. The original simulator spread its changes uniformly across the
  whole 48-hour dataset, which a time-window comparison couldn't see. Fixed
  in `IncidentSimulationService` — see `docs/architecture.md` for details.
  This didn't change any public API or existing test's assertions, only
  *which* transactions within a segment get selected.
