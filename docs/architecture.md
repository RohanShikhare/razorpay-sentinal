# Architecture

Razorpay Sentinel is a **monolith**, deliberately. Detection, diagnosis, policy
gating, recovery, and audit all live in one deployable backend so the
workflow can stay a single, traceable pipeline instead of a distributed
system with its own failure modes. This document describes the shape of that
monolith as of Module 3 (Monitoring & Incident Detection Engine) and where
each future layer will sit.

## Overview

```
┌─────────────────────────────┐        ┌───────────────────────────────────────┐
│   Frontend (Next.js)         │  HTTP  │   Backend (Spring Boot, monolith)      │
│   React + TypeScript         │ ─────► │   /api/v1/*                            │
│   Tailwind CSS                │ ◄───── │                                        │
└─────────────────────────────┘        │  controller → service → repository     │
                                        │  cross-cutting: config, exception,     │
                                        │  dto, monitoring, ai, policy,          │
                                        │  recovery, audit                       │
                                        └──────────────┬──────────────────────────┘
                                                       │ JDBC
                                                       ▼
                                              ┌──────────────────┐
                                              │   PostgreSQL      │
                                              └──────────────────┘
```

## Frontend

- **Next.js 14 (App Router) + React + TypeScript.**
- Tailwind CSS for styling; no component library is imposed.
- `src/lib/api.ts` is the single place that talks to the backend over HTTP —
  later modules should add functions here rather than calling `fetch`
  directly from components.
- `src/lib/config.ts` resolves the backend base URL from
  `NEXT_PUBLIC_API_BASE_URL`.
- Recharts (added in Module 3) powers the failure-rate and segment charts on
  `/monitoring` and `/incidents/[id]`.

## Backend

- **Java 17, Spring Boot 3, Maven.**
- Layered per request: `controller` (HTTP only) → `service` (business logic)
  → `repository` (persistence). Controllers never contain business logic.
- `config/` holds cross-cutting Spring configuration (currently CORS).
- `dto/` holds request/response shapes exchanged with the frontend, kept
  separate from JPA entities so the API contract can evolve independently of
  the schema.
- `exception/` provides `SentinelException` (and `ResourceNotFoundException`)
  as the base for domain errors, plus a `GlobalExceptionHandler` that turns
  any exception into a consistent `ErrorResponse` JSON body. Later modules
  should throw domain exceptions rather than handling errors ad hoc.
- All backend routes are namespaced under `/api/v1`.

## Database

- **PostgreSQL**, configured entirely through environment variables
  (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — see the root README). No secrets
  are hardcoded.
- `spring.jpa.hibernate.ddl-auto` is set to `update` for now, which is
  appropriate while the schema is still taking shape. This should be
  reconsidered (e.g. replaced with a migration tool) once the schema
  stabilizes.
- Module 1 introduced the first table: `payments`, backing the `Payment`
  entity, indexed on `status`, `bank`, `payment_method`, `error_code`, and
  `transaction_timestamp` since those are exactly the columns later modules
  (monitoring, revenue-at-risk) will filter and aggregate on.
- Module 2 added `simulation_runs` and `simulation_changes`, backing
  `SimulationRun`/`SimulationChange`. These are simulation metadata, not a
  new domain table for payments themselves - the simulator still mutates
  rows in `payments` directly (only `status`/`error_code`), it just also
  records what each row's values were beforehand so a reset can restore them.
- Module 3 added `incidents`, backing the `Incident` entity - the first
  table that represents something the system *concluded* rather than raw
  or simulated transaction data.
- Tests run against an in-memory H2 database (test scope only, see
  `pom.xml`) so the full JPA/Hibernate stack can be exercised without a
  live PostgreSQL instance. Production configuration uses PostgreSQL
  exclusively — H2 never appears outside `src/test`.

## Monolithic architecture

Everything above runs as one Spring Boot process and one Next.js app. There
is no message queue, no service mesh, and no separate microservice per
capability. The seven-stage pipeline (Detect → Diagnose → Quantify → Decide →
Gate → Recover → Audit) is expressed as a sequence of in-process service
calls, which keeps the whole decision trail inspectable and makes the audit
log trivial to reason about — every step happened in one process, in order.

## Incident Simulator vs. Incident Detection Engine

These are easy to conflate, so the distinction is explicit:

- **Incident Simulator** (`service/IncidentSimulationService`, Module 2,
  implemented) *creates* controlled, reproducible payment degradation in the
  `payments` table. It reports what it did (`affectedTransactions`,
  `failureRateBefore/After`, ...) but never claims an incident was detected.
  It is deliberately deterministic and requires no AI.
- **Incident Detection Engine** (`monitoring/`, Module 3, implemented)
  *reads* the same `payments` table - whether its abnormal state came from
  the simulator or real traffic - and independently decides whether what it
  sees crosses a configured threshold. Neither side is aware of the other's
  internals: the detector has no special-cased knowledge of simulator
  scenario names, and the simulator has no knowledge of detection thresholds.

**The current engine detects abnormal payment behavior deterministically.
Root-cause diagnosis is intentionally separated and will be handled by the
AI investigation module.** `Incident.rootCause` stays `null` until that
future module assigns one - the deterministic detector never guesses.

## Monitoring & Incident Detection Engine (Module 3)

Lives in the `monitoring/` package: `PaymentStatistics` and `SegmentAnalyzer`
are pure, side-effect-free statistics helpers (easy to unit test in
isolation); `IncidentDetectionService` orchestrates them and is the only
piece that touches the database or persists `Incident` rows.

**Core flow:** payment data → baseline statistics → current-window
statistics → compare → threshold evaluation → incident detected or not.

**Baseline vs. current window.** The baseline is every payment older than
the current monitoring window; the current window is payments timestamped
within the last `windowMinutes` (default 60, configurable per-request and
via `sentinel.monitoring.default-window-minutes`). If the current window
contains too few transactions to be statistically meaningful (below
`sentinel.monitoring.minimum-sample-size`, default 100), the window is
doubled - up to `sentinel.monitoring.max-window-minutes` - until it does.
This is a genuine statistical safeguard: a handful of transactions in an
arbitrarily narrow window would produce meaningless percentage swings
either way, and it's why the primary demo scenario's simulated degradation
is concentrated in *recent* transactions rather than spread evenly across
the whole dataset (see the Module 2 note above) - a time-window comparison
can only detect a change that actually happened recently.

**Segment detection.** The engine never looks only at the global failure
rate. `SegmentAnalyzer` compares baseline vs. current for every bank,
payment method, bank+payment-method combination, and merchant with enough
combined volume to trust (`sentinel.monitoring.minimum-segment-sample-size`,
default 15), so a segment-concentrated issue like Bank X + UPI is caught
even when the system-wide rate barely moves. Whichever signal - global or
the most-deviated segment - shows the larger increase drives severity and
`incidentType`:
- top segment is a merchant → `MERCHANT_DEGRADATION`
- top segment is a bank/method/bank+method combo → `PAYMENT_DEGRADATION`
- no segment stands out more than the global rate → `SYSTEM_DEGRADATION`

**Severity thresholds** (percentage-point increase over baseline, bound via
`sentinel.monitoring.thresholds.*`): below `minimum-increase-points`
(default 5.0) is normal noise, then LOW / MEDIUM / HIGH / CRITICAL at 5 / 12
/ 20 / 30 points by default. All configurable; none hardcoded in the service.

**Revenue at risk.**
```
revenueAtRisk = failedTransactionValue × max(0, (currentFailureRate − baselineFailureRate) / currentFailureRate)
```
i.e. the share of currently-failed value attributable to the *excess*
failure rate over baseline - not all failures, since baseline already has
some "normal" failures that aren't the incident's doing.

**Confidence** is a deterministic 0-100 score (not an AI confidence score),
combining three capped, weighted components: deviation magnitude relative
to the critical threshold (≤40 pts), affected-transaction sample size
relative to a reference size of 200 (≤30 pts), and how much more
concentrated the top segment is versus the global rate (≤30 pts). See
`IncidentDetectionService.computeConfidence` for the exact formula.

**Duplicate suppression.** Before persisting, the engine looks for an
existing non-RESOLVED incident with the same `type` + target
bank/method/merchant and refreshes it (`Incident.refresh`) instead of
creating a new row, so repeated `POST /monitoring/detect` calls against an
unchanged condition don't pile up duplicate incidents.

**What this engine does not do:** call an LLM, assign `rootCause`,
recommend or execute recovery. See "Future layers" below.

## Incident lifecycle (as implemented so far)

`IncidentStatus` has six values (`DETECTED`, `INVESTIGATING`,
`RECOVERY_PENDING`, `RECOVERING`, `RESOLVED`, `ESCALATED`), but Module 3
only ever creates or refreshes incidents in `DETECTED` - the rest of the
lifecycle (investigation, recovery, resolution, escalation) is deliberately
unimplemented scaffolding for later modules, present now so the schema
doesn't need to change when they land.

## Future layers (not implemented yet)

These packages exist as empty, documented placeholders (`package-info.java`)
so later modules slot in without restructuring the project. `monitoring/`
graduated from placeholder to implemented in Module 3 (see above) - the
remaining ones are still ahead:

- **`ai/`** — the AI layer. Root-cause analysis over a detected incident, and
  a bounded recovery recommendation. This is where an LLM call will live,
  isolated behind a service interface so the rest of the system doesn't
  depend on a specific provider. Will read `Incident` rows Module 3 produces
  and write to `Incident.rootCause`, which the detector deliberately never
  touches.
- **`policy/`** — deterministic guardrails that validate whatever the AI
  layer recommends before anything is allowed to execute. This is the gate
  in Detect → Diagnose → Quantify → Decide → **Gate** → Recover → Audit, and
  it is intentionally *not* AI — it's rules a human can read and trust.
- **`recovery/`** — simulated execution of an approved recovery strategy,
  and the resulting money-recovered accounting.
- **`audit/`** — a complete, queryable record of every detection, diagnosis,
  decision, policy check, and recovery action, for after-the-fact review and
  evaluation.

See `development-roadmap.md` for the full module sequence.
