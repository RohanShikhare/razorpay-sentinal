# Razorpay Sentinel

**AI Payment Incident Commander**

Sentinel watches Razorpay payment traffic for abnormal failure patterns,
diagnoses why they're happening, quantifies the revenue at risk, and proposes
a bounded recovery strategy — one that a deterministic policy engine has to
approve before anything is allowed to run.

> **Status: Module 6 — AI Diagnostic Agent.** Foundation, synthetic payment
> dataset, incident simulator, monitoring & detection engine, financial impact
> engine, command center dashboard, and the advisory AI Diagnostic Agent are
> implemented. Policy gating and recovery execution described below are
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

**Module 4 — Revenue-at-Risk & Financial Impact Engine**

- `financial/` package: `RecoverabilityLevel`, `RecoverabilityClassifier`
  (fixed, documented error-code → recoverability mapping), and
  `FinancialImpactService` — reads Module 3's `revenueAtRisk` rather than
  recomputing it (see
  [Monitoring & Incident Detection Engine](#monitoring--incident-detection-engine)
  and `docs/architecture.md` for why that keeps it an explainable snapshot).
- `RecoverabilityProperties` — configurable numeric rate per recoverability
  level (`sentinel.financial.recoverability-rates.*`).
- Affected-batch reconstruction straight from `payments` (no duplicated
  transaction storage) using each incident's own target criteria.
- `GET /api/v1/incidents/{id}/financial-impact`,
  `/financial-impact/breakdown` (by bank / payment method / error code /
  merchant), and `/evidence` (structured, map-shaped data ready for a
  future AI module — no LLM call happens here).
- `GET /api/v1/monitoring/summary` extended with `estimatedRecoverableRevenue`
  and `affectedTransactionValue`.
- A financial-impact section on the incident detail page (summary cards,
  a recoverable-vs-at-risk progress bar, and revenue-at-risk-by-segment
  charts), plus the extra fields surfaced on the Monitoring command center.
- Unit tests using hand-built, controlled-amount payments to verify the
  formulas exactly, plus integration tests tying together simulate →
  detect → financial-impact for the Bank X UPI and merchant scenarios.

**Module 5 — Command Center Dashboard**

- `/dashboard` — the new primary operations console, consolidating data
  already exposed by Modules 1–4 (no parallel data path): KPI cards
  (success rate vs. baseline, revenue at risk, estimated recoverable
  revenue, active incidents, affected payments), an active-incident hero
  card, bank/payment-method/error-code breakdown tables, a financial
  exposure section, a recent-incidents table, and a quick-operations panel
  (simulate/detect/reset).
- `PaymentSummaryResponse`'s breakdowns extended with per-segment failure
  count/value/rate (`byBank`/`byPaymentMethod`) and value/percentage-of-failures
  (`byErrorCode`) — an additive extension of the existing Module 1 endpoint,
  not a new aggregation service, so the dashboard's breakdown tables work
  even in the healthy state.
- A shared `IncidentsTable` component used by both `/incidents` and
  `/dashboard` (no duplicate table implementation).
- Manual refresh plus 20-second auto-refresh polling, guarded against
  overlapping requests; proper loading/error/empty states throughout.
- `/`, `/payments`, `/simulator`, `/monitoring`, and `/incidents` remain
  fully intact — the Command Center consolidates and links to them, it
  doesn't replace them.

**Module 6 — AI Diagnostic Agent**

- Provider abstraction (`AiProvider`, `MockAiProvider`, `GeminiAiProvider`, `AiProviderFactory`) supporting configurable live Gemini models or deterministic, offline mock diagnoses.
- Deterministic incident evidence pipeline (`IncidentEvidenceService`) formalizing telemetry and Module 4 financial metrics for AI ingestion.
- Strict bounded AI response schema (`ControlledAction`, `RiskLevel`, `DiagnosisStatus`) and conservative human approval gate.
- Hallucination guards: "DATA, NOT INSTRUCTIONS" prompt injection defense, strict JSON schema validation, bounds checks (confidence 0–1, recovery % 0–100), and rejecting unsupported free text.
- Persistence: `incident_diagnoses` JPA entity and repository storing diagnostic audit history.
- REST API: `POST /api/v1/incidents/{id}/diagnose` (on-demand investigation trigger) and `GET /api/v1/incidents/{id}/diagnosis` (retrieve latest).
- Frontend: `AiInvestigationPanel` integrated into `/incidents/[id]`, featuring an on-demand "Investigate with AI" trigger, analyzing spinner, root cause, confidence meter, observed evidence bullets, reasoning narrative, bounded action card, advisory recovery %, risk badge, and a safety guardrail banner.
- Active incident quick-link on `/dashboard`.

### Not implemented yet

- Authentication (intentionally out of scope for this internal tool for now).
- Policy/guardrail validation engine (Modules 8–10 will handle recovery strategy, policy gates, and execution simulation).
- Recovery execution (simulated), audit trail, and evaluation — so there is no "actually recovered" figure yet, only `estimatedRecoverableRevenue`.
- Incident lifecycle transitions beyond `DETECTED` (no resolution workflow yet).

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
│       │   │   ├── config/         # CORS, monitoring thresholds, recoverability rates
│       │   │   ├── controller/     # HTTP endpoints only
│       │   │   ├── dto/            # request/response shapes
│       │   │   ├── entity/         # Payment, Simulation*, Incident + enums (Modules 1-3)
│       │   │   ├── exception/      # SentinelException + global handler
│       │   │   ├── repository/     # PaymentRepository, Simulation*Repository, IncidentRepository
│       │   │   ├── service/        # business logic (payments, simulation)
│       │   │   ├── monitoring/     # deterministic detection engine (Module 3)
│       │   │   ├── financial/      # deterministic financial-impact engine (Module 4)
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
│       ├── app/                # Next.js App Router pages (/, /dashboard, /payments, /simulator, /monitoring, /incidents, /incidents/[id])
│       ├── components/         # UI components (dashboard/, payments/, simulator/, monitoring/, financial/)
│       └── lib/                # api.ts, payments.ts, simulations.ts, monitoring.ts, financial.ts, config.ts, navigation.ts
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
| `SENTINEL_RECOVERABILITY_HIGH` / `_MEDIUM` / `_LOW` / `_UNKNOWN` | `0.70` / `0.35` / `0.10` / `0.25` | Recovery rate applied per recoverability level (see `docs/architecture.md`) |
| `AI_PROVIDER` | `mock` | AI diagnostic provider (`mock` for deterministic/offline testing, `gemini` for live LLM) |
| `AI_API_KEY` | *(empty)* | Google Gemini API key (required only when `AI_PROVIDER=gemini`) |
| `AI_MODEL` | `gemini-1.5-flash` | Gemini model name |
| `AI_TEMPERATURE` | `0.1` | Model sampling temperature (0.0 - 1.0) |
| `AI_TIMEOUT_MS` | `10000` | HTTP request timeout in milliseconds for live AI calls |

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

`byPaymentMethod` and `byBank` entries (`key`, `count`, `value`) were
extended in Module 5 with `failedCount`, `failedValue`, and `failureRate`
per segment. `byErrorCode` entries (`key`, `count`) were extended with
`value` and `percentageOfFailures`. Both extensions reuse the existing
Module 1 aggregation service (`PaymentService`/`PaymentRepository`) —
additive fields only, nothing removed — so the Command Center's breakdown
tables work from a single endpoint even when no incident is active.

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

## Revenue-at-Risk & Financial Impact Engine

**Revenue-at-risk is calculated from the actual affected transaction
population. No financial values are hardcoded into the dashboard.**

This builds on the `revenueAtRisk` figure the Monitoring engine already
computes and persists on each `Incident` — this module reads that value
rather than recomputing it (see `docs/architecture.md` for why that keeps
it an explainable snapshot), and adds a recoverable/non-recoverable split
plus per-segment breakdowns derived live from the affected transaction
population.

### Financial impact

```
GET /api/v1/incidents/{id}/financial-impact
```

```json
{
  "incidentId": 123,
  "affectedTransactions": 1847,
  "affectedTransactionValue": 2470000.00,
  "failedTransactionValue": 985000.00,
  "revenueAtRisk": 812000.00,
  "estimatedRecoverableRevenue": 552160.00,
  "nonRecoverableRevenue": 259840.00,
  "averageTransactionValue": 1337.30,
  "largestAffectedTransaction": 148500.00,
  "recoverablePercentage": 68.0
}
```

Formulas (full detail in `docs/architecture.md`):

```
affectedTransactionValue = Σ amount over the affected population
failedTransactionValue   = Σ amount over the FAILED subset
revenueAtRisk             = read from the Incident (Monitoring engine's formula)
estimatedRecoverableRevenue = revenueAtRisk × (value-weighted recoverability rate across the failed population's error-code mix)
nonRecoverableRevenue       = revenueAtRisk − estimatedRecoverableRevenue
```

`estimatedRecoverableRevenue` and `nonRecoverableRevenue` always sum
exactly to `revenueAtRisk` (computed by subtraction, not independently).

### Financial breakdown

```
GET /api/v1/incidents/{id}/financial-impact/breakdown
```

Returns `byBank`, `byPaymentMethod`, `byErrorCode`, and `byMerchant` — each
entry has `transactionCount`, `transactionValue`, `failedValue`,
`revenueAtRisk`, and `estimatedRecoverableRevenue`. Segment `revenueAtRisk`
values within one dimension sum back to the incident's total.

### Evidence (for the future AI module)

```
GET /api/v1/incidents/{id}/evidence
```

The same numbers, shaped as map-keyed breakdowns instead of lists — ready
for a future AI investigation module to consume directly. No LLM call
happens here; this only proves the data is ready.

### Recoverability rates

| Error code | Level | Default rate |
|---|---|---|
| `BANK_TIMEOUT`, `NETWORK_ERROR`, `RATE_LIMIT` | HIGH_RECOVERABILITY | 0.70 |
| `ISSUER_DECLINED` | MEDIUM_RECOVERABILITY | 0.35 |
| `INSUFFICIENT_FUNDS` | LOW_RECOVERABILITY | 0.10 |
| `UNKNOWN` | UNKNOWN | 0.25 |

Illustrative business assumptions, configurable via
`sentinel.financial.recoverability-rates.*` — see Environment Variables above.

## Command Center Dashboard

```
/dashboard
```

The project's primary operations view, built entirely on the existing
Modules 1–4 APIs — no financial or statistical values are computed
client-side:

| Section | Data source |
|---|---|
| KPI cards (success rate, revenue at risk, estimated recoverable, active incidents, affected payments) | `GET /api/v1/monitoring/summary` |
| Active incident hero card | `GET /api/v1/incidents` (most severe/recent) + `GET /api/v1/incidents/{id}/financial-impact` |
| Failure rate chart | `GET /api/v1/monitoring/summary` (baseline vs. current) |
| Financial exposure section | `GET /api/v1/incidents/{id}/financial-impact` (reuses the same `FinancialImpactSummary` component as the incident detail page) |
| Bank / payment-method / error-code breakdown tables | `GET /api/v1/payments/summary` (extended in Module 5, see above) |
| Recent incidents table | `GET /api/v1/incidents` (shared `IncidentsTable` component, also used by `/incidents`) |
| Quick operations panel | `POST /api/v1/simulations/run`, `POST /api/v1/monitoring/detect`, `POST /api/v1/simulations/reset` |

The dashboard auto-refreshes every 20 seconds (guarded against overlapping
in-flight requests) and has a manual refresh button. `Root Cause: Not yet
investigated` is always shown on the active-incident hero card — the
deterministic detector never guesses a root cause, and the UI doesn't
either, even for the Bank X UPI demo scenario. `/`, `/payments`,
`/simulator`, `/monitoring`, and `/incidents` remain fully functional and
reachable from navigation; the Command Center consolidates and links to
them rather than replacing them.

## AI Diagnostic Agent API (Module 6)

The AI Diagnostic Agent evaluates deterministic statistical evidence and formulates an advisory root-cause hypothesis, diagnostic reasoning narrative, and bounded action recommendation.

### Trigger AI diagnosis

```
POST /api/v1/incidents/{incidentId}/diagnose
```

Generates structured evidence, invokes the configured `AiProvider`, validates response schema and ranges, applies safety and human approval gates, persists the result to `incident_diagnoses`, and updates the incident's `rootCause` and `status` to `INVESTIGATING`.

Example response:

```json
{
  "diagnosisId": 1,
  "incidentId": 123,
  "timestamp": "2026-09-04T12:00:00Z",
  "provider": "mock",
  "model": "deterministic-mock-v1",
  "incidentType": "PAYMENT_DEGRADATION",
  "rootCause": "Downstream UPI gateway degradation on Bank X core banking network resulting in elevated BANK_TIMEOUT error rates.",
  "reasoning": "Telemetry evidence shows a localized failure surge specifically for Bank X UPI transactions (+32.4 pts increase), with BANK_TIMEOUT accounting for 87.5% of failures. Other payment methods and banks remain unaffected.",
  "confidence": 0.94,
  "evidence": [
    "Bank X UPI failure rate surged from baseline 4.2% to 36.6%",
    "BANK_TIMEOUT is the dominant failure mode (87.5% concentration)",
    "Non-Bank-X transactions operating at nominal baseline (3.8% failure rate)"
  ],
  "affectedSegment": "BANK: Bank X, METHOD: UPI",
  "recommendedAction": "ALTERNATE_PAYMENT_METHOD",
  "estimatedRecoveryPercentage": 75,
  "riskLevel": "MEDIUM",
  "requiresHumanApproval": false,
  "status": "COMPLETED",
  "statusReason": null
}
```

### Get latest AI diagnosis

```
GET /api/v1/incidents/{incidentId}/diagnosis
```

Returns the latest persisted diagnosis for an incident if available, or 404 if not yet investigated.

## Build verification notes

- **Backend**: Complete build and test suite verified via Maven (`mvn test`). All 57 tests across Modules 0 to 6 passed with 0 failures and 0 errors:
  - `AiDiagnosticControllerIntegrationTest` (2 tests)
  - `AiDiagnosticServiceTest` (6 tests)
  - `IncidentEvidenceServiceTest` (1 test)
  - `MonitoringControllerIntegrationTest` (7 tests)
  - `PaymentControllerIntegrationTest` (8 tests)
  - `SimulationControllerIntegrationTest` (5 tests)
  - `FinancialImpactServiceTest` (8 tests)
  - `PaymentGeneratorServiceTest` (8 tests)
  - `PaymentStatisticsTest` (5 tests)
  - `SegmentAnalyzerTest` (4 tests)
  - `RazorpaySentinelApplicationTests` (1 test)
  - `HealthControllerTest` (2 tests)
- **Frontend**: `npm run build` executed against Next.js 14 App Router, successfully compiling all 9 static and dynamic routes with zero TypeScript or ESLint errors.
