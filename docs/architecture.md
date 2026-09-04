# Architecture

Razorpay Sentinel is a **monolith**, deliberately. Detection, diagnosis, policy
gating, recovery, and audit all live in one deployable backend so the
workflow can stay a single, traceable pipeline instead of a distributed
system with its own failure modes. This document describes the shape of that
monolith as of Module 4 (Revenue-at-Risk & Financial Impact Engine) and
where each future layer will sit.

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
- `/dashboard` (Module 5) is the Command Center - the project's primary
  operations view. It is a *consolidation* layer: it calls the same
  Modules 1-4 endpoints the standalone `/payments`, `/simulator`,
  `/monitoring`, and `/incidents` pages already use, and reuses their
  components where the shape matches exactly (`ScenarioSelector`,
  `FailureRateComparisonChart`, `FinancialImpactSummary`). It introduces no
  new data path and computes no financial or statistical values
  client-side. `components/monitoring/IncidentsTable.tsx` was extracted
  as a shared component used by both `/incidents` and `/dashboard`,
  avoiding a duplicate table implementation.

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
- Module 5 extended `PaymentService.getSummary()`/`PaymentRepository`
  (Module 1) with per-segment failure count/value/rate rather than adding a
  new aggregation service - the Command Center dashboard's bank/payment-method/
  error-code breakdown tables read from the same, now-richer,
  `GET /api/v1/payments/summary` response. Purely additive fields; nothing
  existing was removed or renamed.

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
- Module 4 added no new tables. Financial impact is deliberately computed
  on demand from `payments` + the `Incident` row's own stored fields,
  rather than persisted anywhere new - see "Financial Impact Engine" below
  for why.
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

## Financial Impact Engine (Module 4)

**Revenue-at-risk is calculated from the actual affected transaction
population. No financial values are hardcoded into the dashboard.**

Lives in the `financial/` package: `RecoverabilityLevel` and
`RecoverabilityClassifier` are pure classification logic; `FinancialImpactService`
is the only piece that touches the database. All monetary values are
`BigDecimal` throughout - never `double`/`float`.

**Core flow:** detected incident → affected transaction population →
financial exposure analysis → revenue at risk → recoverable revenue
estimate → batch-level metrics.

**Revenue at risk is read, not recomputed.** `Incident.revenueAtRisk` is a
value Module 3's `IncidentDetectionService` computes and persists at
(re)detection time (see the Monitoring section above for its formula). This
module treats that value as the historical snapshot of "how much revenue
was at risk when this was detected" and never overwrites it — only a
genuine re-detection event (`Incident.refresh`) does. That is precisely
what keeps the top-line financial-impact numbers explainable even if the
underlying payment data changes later (e.g. a future recovery module, or a
simulation reset) — the incident snapshot requirement from Module 4's spec
is satisfied by *not* recomputing this number here, rather than by adding a
new snapshot table.

**Affected batch reconstruction.** No transactions are duplicated into a
second table. `FinancialImpactService.resolveAffectedBatch` derives the
population on demand from the existing `payments` table using the
incident's own deterministic criteria — `targetBank` / `targetPaymentMethod`
/ `targetMerchantId` (whichever apply to that incident's type) plus every
payment from `startTime` onward. Segment breakdowns and the recoverability
estimate are computed *live* from this batch each call, so — unlike the
top-line revenue-at-risk — they reflect the current state of the data.

**Recoverability classification** (`RecoverabilityClassifier`, fixed
business rules in code):

| Error code | Level | Reasoning |
|---|---|---|
| `BANK_TIMEOUT`, `NETWORK_ERROR`, `RATE_LIMIT` | HIGH_RECOVERABILITY | Transient infrastructure/rail issues, often resolved by a retry |
| `ISSUER_DECLINED` | MEDIUM_RECOVERABILITY | Sometimes a temporary risk hold, often not |
| `INSUFFICIENT_FUNDS` | LOW_RECOVERABILITY | Customer-side condition unlikely to resolve on immediate retry |
| `UNKNOWN` (and anything unmapped) | UNKNOWN | Not enough information to judge |

The numeric rate applied per level is configuration
(`sentinel.financial.recoverability-rates.*`, defaults 0.70 / 0.35 / 0.10 /
0.25) — illustrative business assumptions, not empirically derived, which
is exactly why they're tunable rather than embedded in the formula.

**Formulas** (see `FinancialImpactResponse`'s Javadoc for the authoritative
version):
```
affectedTransactionValue = Σ amount over the affected population (any status)
failedTransactionValue   = Σ amount over the FAILED subset of that population
revenueAtRisk             = read from Incident (Module 3's formula)
weightedRate               = Σ(failedValue[errorCode] × rate[level(errorCode)]) / totalFailedValue
estimatedRecoverableRevenue = revenueAtRisk × weightedRate
nonRecoverableRevenue       = revenueAtRisk − estimatedRecoverableRevenue   (by subtraction, so the two always sum exactly)
averageTransactionValue     = affectedTransactionValue / affectedTransactions (0 if none)
recoverablePercentage        = estimatedRecoverableRevenue / revenueAtRisk × 100 (0 if revenueAtRisk is 0)
```
Per-segment `revenueAtRisk` (in the breakdown endpoint) apportions the
incident's total by that segment's share of the population's total failed
value, so segment values sum back to the incident total.

**Distinction between revenue-at-risk and actual recovered revenue.**
`estimatedRecoverableRevenue` is exactly that — an estimate, computed
deterministically from error-code composition. There is no "actually
recovered" figure yet, because no recovery module exists yet; that
distinction (estimate vs. actual) is preserved deliberately so a future
recovery module has an unambiguous baseline to report against.

**Structured evidence for the future AI module.** `FinancialImpactService.getEvidence`
(exposed at `GET /api/v1/incidents/{id}/evidence`) packages the same
numbers as map-keyed breakdowns (`bankBreakdown`, `paymentMethodBreakdown`,
`errorCodeBreakdown`, `merchantBreakdown`) rather than lists, which tends to
be a more convenient shape for prompt construction. No LLM call happens
here — this only proves the data is ready to hand off.

**What this engine does not do:** execute recovery, retry transactions,
route to alternate rails, or call an LLM.

## Incident lifecycle (as implemented so far)

`IncidentStatus` has six values (`DETECTED`, `INVESTIGATING`,
`RECOVERY_PENDING`, `RECOVERING`, `RESOLVED`, `ESCALATED`), but Module 3
only ever creates or refreshes incidents in `DETECTED` - the rest of the
lifecycle (investigation, recovery, resolution, escalation) is deliberately
unimplemented scaffolding for later modules, present now so the schema
doesn't need to change when they land.

## Module 6: AI Diagnostic Agent Architecture

Module 6 introduces the AI Diagnostic Agent adhering to the strict architectural principle:
**Detect → Evidence → AI Diagnosis → Recommendation → Policy Gate → Recovery**

### 1. Strict Advisory Boundary
The AI Diagnostic Agent is strictly **advisory**:
- Receives structured, deterministic telemetry evidence from `IncidentEvidenceService` (Module 4).
- Synthesizes an explanatory root cause, diagnostic reasoning, confidence score, and bounded action recommendation.
- **NEVER** executes payments, retries transactions, modifies payment records, triggers recovery, or bypasses policy gates.
- **NEVER** overrides deterministic financial numbers (`revenueAtRisk`, `estimatedRecoverableRevenue`, `nonRecoverableRevenue`, `affectedTransactionValue`).
- **NEVER** queries the database directly or invents hallucinated data values.

### 2. Provider Abstraction (`com.sentinelpulse.ai`)
- `AiProvider`: Core interface for evaluating structured telemetry evidence.
- `MockAiProvider`: Deterministic, zero-dependency provider for local development, CI/CD, and automated tests. Correctly attributes root cause and bounded actions for Bank X UPI degradation, merchant degradation, and normal statistical noise without requiring external network calls or API keys.
- `GeminiAiProvider`: Production REST client using Spring Boot's built-in `RestClient` for direct interaction with Google Gemini API models (`gemini-1.5-flash`, etc.).
- `AiProviderFactory`: Automatically wires the active provider based on `sentinel.ai.provider` (`mock` or `gemini`).

### 3. Hallucination & Safety Guardrails
- **Prompt Injection Defense**: All telemetry fields and merchant identifiers are treated strictly as **DATA, NOT INSTRUCTIONS**.
- **Strict Bounded Schema**: The response is validated against strict domain types:
  - `ControlledAction`: `IMMEDIATE_RETRY`, `DELAYED_RETRY`, `ALTERNATE_PAYMENT_METHOD`, `MONITOR`, `HUMAN_REVIEW`, `NO_ACTION`. Uncontrolled strings are strictly rejected.
  - `RiskLevel`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
  - `confidence`: Verified strictly between `0.0` and `1.0`.
  - `estimatedRecoveryPercentage`: Verified strictly between `0` and `100`.
- **Conservative Human Approval Gate**: `requiresHumanApproval` is enforced if risk is HIGH/CRITICAL or confidence is low, signaling that human or policy authorization is required before downstream recovery.

### 4. Diagnosis Persistence (`incident_diagnoses` table)
- Each diagnosis is persisted in `incident_diagnoses` with timestamp, provider, model, root cause, reasoning, evidence points, recommended action, advisory recovery %, risk level, human approval flag, and status (`REQUESTED`, `COMPLETED`, `FAILED`, `INVALID_RESPONSE`).
- Successfully completed diagnoses update `incidents.root_cause` and transition the incident status to `INVESTIGATING`.

### 5. API Endpoints
- `POST /api/v1/incidents/{incidentId}/diagnose`: Triggers an AI investigation on demand.
- `GET /api/v1/incidents/{incidentId}/diagnosis`: Retrieves the latest existing diagnosis without re-invoking the LLM.

## Module 7: Root-Cause Investigation Architecture

Module 7 builds on Module 6 by formalizing a structured **hypothesis evaluation and evidence comparison** engine:
$$\text{Incident} \longrightarrow \text{Telemetry Evidence} \longrightarrow \text{Segment Analysis} \longrightarrow \text{Competing Hypotheses} \longrightarrow \text{Evidence Comparison} \longrightarrow \text{Strongest Root Cause} \longrightarrow \text{Confidence} \longrightarrow \text{Investigation Report}$$

### 1. Competing Hypotheses Formulation
Rather than outputting a single unexamined deduction, the Root-Cause Investigation Agent formulates 2 to 4 competing hypotheses across operational dimensions:
- **Downstream Bank / Gateway switch failure**: Investigates specific bank + payment method concentrations.
- **Merchant Integration / Credentials failure**: Evaluates whether failures are 100% isolated to a single merchant across multiple banks.
- **Protocol / National Switch Outage**: Evaluates whether failures affect all banks utilizing a given protocol (e.g. NPCI UPI central switch).
- **Stochastic Baseline Noise**: Evaluates whether fluctuations represent diffuse background variance without statistically significant concentration.

### 2. Fact vs Inference Telemetry Corroboration
The investigation strictly separates:
- **Observed Facts**: Deterministic metrics directly present in telemetry (e.g., error code distributions, baseline vs current failure rates, merchant volume breakdowns).
- **AI Inference**: Diagnostic deduction ranking which hypothesis is corroborated or refuted by the facts.

### 3. Investigation Persistence (`incident_investigations` table)
- Persisted in `incident_investigations` with timestamp, provider, model, summary, strongest root cause, overall confidence, affected segment, reasoning, uncertainty, human review recommendations, and serialized JSON arrays of evaluated hypotheses.
- Statuses: `REQUESTED`, `COMPLETED`, `FAILED`, `INVALID_RESPONSE`.

### 4. Investigation Endpoints
- `POST /api/v1/incidents/{incidentId}/investigate`: Triggers deep root-cause hypothesis evaluation on demand.
- `GET /api/v1/incidents/{incidentId}/investigation`: Retrieves the latest persisted root-cause investigation report without re-invoking the LLM.

## Future layers (not implemented yet)

These packages exist as placeholders (`package-info.java`) so future modules slot in cleanly:

- **`recovery/`** (Module 8 & 10) — AI-generated bounded recovery strategy recommendations and simulated recovery execution.
- **`policy/`** (Module 9) — deterministic guardrails that validate whatever the AI layer recommends before anything is allowed to execute. This is the gate in Detect → Diagnose → Quantify → Decide → **Gate** → Recover → Audit.
- **`audit/`** (Module 11) — a complete, queryable record of every detection, diagnosis, decision, policy check, and recovery action, for after-the-fact review and evaluation.

See `development-roadmap.md` for the full module sequence.
