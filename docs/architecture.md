# Architecture

Razorpay Sentinel is a **monolith**, deliberately. Detection, diagnosis, policy
gating, recovery, and audit all live in one deployable backend so the
workflow can stay a single, traceable pipeline instead of a distributed
system with its own failure modes. This document describes the shape of that monolith as of Module 9
(Deterministic Policy / Guardrail Engine) and where each future layer will sit.

## Pipeline Architecture

```text
Detect → Diagnose → Investigate → Quantify → Decide (Module 8) → GATE (Module 9) → Recover (Module 10) → Audit (Module 11)
```

The core architectural invariant governing recovery automation is:

1. **AI Proposes (Modules 6, 7, 8)**: Formulates diagnosis hypotheses and advisory recovery strategy proposals with stopping conditions.
2. **Policy Decides (Module 9)**: 100% deterministic, zero-AI guardrail engine evaluates proposals against hard financial bounds, retry limits, merchant protections, and safety invariants.
3. **Execution Obeys Policy (Module 10)**: Autonomous execution is strictly blocked unless the Policy Gate explicitly grants `executionAllowed = true` within enforced constraints.

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

## Module 8: Recovery Strategy Engine Architecture

Module 8 determines what bounded recovery strategy Sentinel should propose based on deterministic telemetry, Module 4 financial impact, Module 6 diagnosis, and Module 7 root-cause investigation:
$$\text{Investigation} \longrightarrow \text{Select / Rank Strategy} \longrightarrow \text{Estimate Recovery} \longrightarrow \text{Define Stopping Conditions} \longrightarrow \text{Strategy Proposal}$$

### 1. Controlled Strategy Types (`com.sentinelpulse.ai.RecoveryStrategyType`)
Uncontrolled or arbitrary actions are strictly prohibited. Proposals must use discrete enums:
- `IMMEDIATE_RETRY`: Transient errors with nominal provider health.
- `DELAYED_RETRY`: Upstream gateway timeout / switch congestion requiring exponential backoff delay.
- `ALTERNATE_PAYMENT_METHOD`: Specific rail degradation with healthy alternative payment options (e.g. Cards/Netbanking).
- `RETRY_WITH_BACKOFF`: Intermittent errors recoverable via bounded stepwise attempts.
- `MONITOR_ONLY`: Diffuse baseline noise or low confidence scenarios avoiding unnecessary customer disruption.
- `HUMAN_REVIEW`: High risk, merchant configuration errors, or conflicting evidence requiring commander inspection.
- `NO_ACTION`: Standard background baseline noise.

### 2. Bounded Stopping Conditions (`com.sentinelpulse.dto.StoppingConditionsDto`)
Every proposed recovery strategy defines explicit operational limits:
- `maxRetryAttempts`: Bounded attempt cap per transaction (e.g. 2 attempts).
- `retryWindowMinutes`: Maximum execution timeframe (e.g. 30 minutes).
- `backoffBaseSeconds`: Exponential backoff initial delay (e.g. 30 seconds).
- `stopOnSuccess`: Immediate termination upon settlement success.
- `stopOnIncidentResolved`: Automatic cancellation if incident recovers.
- `failureRateThreshold`: Downstream circuit-breaker if retry error rate exceeds threshold (e.g. 50%).
- `escalateOnFailureLimit`: Automatic escalation to human review upon attempt exhaustion.

### 3. Authoritative Financial Anchor vs Strategy Estimates
- Module 4 remains the authoritative source of financial truth (`revenueAtRisk`, `estimatedRecoverableRevenue`, `nonRecoverableRevenue`).
- Strategy recovery is clearly marked as an **advisory estimate**:
  $$\text{estimatedRecoveryAmount} = \min(\text{authoritativeRecoverable}, \text{authoritativeRecoverable} \times \frac{\text{expectedRecoveryPercentage}}{100})$$
- Guaranteed bounds: $0 \le \text{estimatedRecoveryAmount} \le \text{authoritativeRecoverable}$.

### 4. Recovery Strategy Persistence (`recovery_strategies` table)
- Persisted in `recovery_strategies` with incident ID, investigation ID, selected strategy, rank, rationale, confidence, risk level, affected segment, authoritative anchors, estimated recovery amount, stopping conditions JSON, alternatives JSON, source (`DETERMINISTIC_RULES`, `AI_ASSISTED`, `FALLBACK`), and status (`PROPOSED`, `SUPERSEDED`, `REJECTED`).
- Re-evaluating a strategy for an active incident marks previous proposals as `SUPERSEDED`.

### 5. API Endpoints
- `POST /api/v1/incidents/{incidentId}/recovery-strategy`: Formulates and persists a bounded recovery strategy proposal.
- `GET /api/v1/incidents/{incidentId}/recovery-strategy`: Retrieves the latest persisted strategy without re-running AI.

## Module 9: Deterministic Policy / Guardrail Engine Architecture

Module 9 serves as the mandatory, unbypassable safety gate between recovery strategy proposals (Module 8) and recovery execution (Module 10):
$$\text{Strategy Proposal} \longrightarrow \text{Deterministic Policy Gate (12 Checks)} \longrightarrow \text{Policy Decision (Enforced Limits)} \longrightarrow \text{Execution}$$

### 1. Architectural Invariant
$$\text{AI proposes (Modules 6, 7, 8)} \longrightarrow \text{Policy decides (Module 9)} \longrightarrow \text{Execution obeys policy (Module 10)}$$
- **Zero AI in Decision Making**: Evaluation is 100% deterministic code.
- **Authoritative Limits**: Strategy parameters exceeding safety thresholds are clamped (`APPROVED_WITH_LIMITS`).

### 2. Policy Evaluation Persistence (`policy_evaluations` table)
- Persisted in `policy_evaluations` table with evaluation timestamp, decision (`APPROVED`, `APPROVED_WITH_LIMITS`, `HUMAN_REVIEW`, `REJECTED`), checks JSON, applied limits JSON, allowed actions JSON, and unbypassable `executionAllowed` boolean flag.

---

## Module 10: Recovery Execution Simulator Architecture

Module 10 is the execution engine that tests the final operational promise:
> **Sentinel does not merely identify lost revenue. It executes a bounded recovery workflow and measures how much revenue was actually recovered.**

### 1. Safety & Policy Gate Boundary
Before attempting any simulated retry, Module 10 independently queries and validates the latest Module 9 policy evaluation:
- Execution is permitted **only** if:
  $$\text{policyDecision} \in \{\text{APPROVED}, \text{APPROVED\_WITH\_LIMITS}\} \quad \text{AND} \quad \text{executionAllowed} == \text{true}$$
- If policy decision is `HUMAN_REVIEW`, `REJECTED`, or `executionAllowed == false`, execution is strictly **BLOCKED** (`status = BLOCKED`, `stoppingReason = POLICY_BLOCKED` or `HUMAN_APPROVAL_REQUIRED`).
- The backend independently validates policy freshness, strategy identity match (`policy.strategyId == strategy.id`), and incident consistency. It never trusts the frontend.

### 2. Authoritative Policy Limits
When Module 9 grants `APPROVED_WITH_LIMITS`, Module 10 enforces the clamped limits:
- `appliedRetryLimit`: Clamped retry attempt ceiling (e.g. 3 attempts max).
- `appliedRetryWindow`: Bounded execution window (e.g. 30 minutes).
- `appliedBackoff`: Configured retry backoff interval.

### 3. Deterministic Synthetic Payment Selection
- Payments are queried deterministically from the incident's affected segment (bank, payment method, merchant, error code, timestamp window).
- No arbitrary, unrelated, or invented transactions enter the simulation.
- Payment source records remain immutable and protected; simulation results are recorded in dedicated execution tables.

### 4. Deterministic Outcome & Recovery Model
Retry success probabilities are simulated deterministically using payment attributes:
- **Transient Gateway / Bank Timeout**: High recovery probability on delayed retry ($P \approx 0.75 - 0.85$).
- **Network Errors / Rate Limits**: Moderate recovery probability after backoff ($P \approx 0.65 - 0.75$).
- **Merchant Degradation**: $0\%$ retry recovery probability (root cause is in merchant integration).
- **Permanent Errors** (`INSUFFICIENT_FUNDS`, `ISSUER_DECLINED`): $0\%$ recovery.
- **Circuit Breaker**: If overall failure rate among attempted transactions exceeds 50% after initial samples, execution halts with `STOPPED` and `stoppingReason = CIRCUIT_BREAKER_FAILURE_RATE`.

### 5. Authoritative Financial Cap
$$\text{actualRecoveredAmount} = \sum_{\text{recovered } p} p.\text{amount} \le \text{authoritativeRecoverableRevenue (Module 4)}$$
- The recovered amount is calculated strictly from successful synthetic payments.
- It is physically impossible for Module 10 to recover more than Module 4's authoritative recoverable revenue.

### 6. Persistence Model
- `recovery_executions` table (`RecoveryExecution` JPA entity): Tracks execution lifecycle, status (`REQUESTED`, `BLOCKED`, `RUNNING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `STOPPED`, `FAILED`), eligible/attempted/successful/failed/skipped transaction counts, actual recovered amount, and stopping conditions.
- `recovery_execution_attempts` table (`RecoveryExecutionAttempt` JPA entity): Transaction-level audit of every synthetic retry attempt with timestamp, attempt number, strategy type, outcome, and failure reason.

### 7. Endpoints
- `POST /api/v1/incidents/{incidentId}/recovery-execute`: Verifies policy gate, runs bounded recovery simulation, and persists results.
- `GET /api/v1/incidents/{incidentId}/recovery-execution`: Retrieves latest execution result.
- `GET /api/v1/recovery-executions/{executionId}`: Retrieves specific execution details and attempts.

---

## Module 11: Audit Trail & Decision History Architecture

Module 11 sits across the entire lifecycle as an **immutable-style, tamper-evident audit and decision history layer**:

```text
AI proposes (Modules 6, 7, 8)
         ↓
Policy decides (Module 9)
         ↓
Execution obeys policy (Module 10)
         ↓
Audit records what happened (Module 11)
```

### 1. Invariants & Boundaries
- **Zero LLM Calls**: Module 11 never invokes AI models or performs decision logic.
- **Zero Business Logic Mutations**: Audit recording is strictly observational and never modifies domain state, policies, or payment records.
- **Fail-Safe Persistence**: Audit persistence failures are logged in isolation without blocking upstream payment, diagnostic, or recovery operations.
- **Authoritative Anchors**: Financial amounts, policy decisions, and execution numbers are ingested directly from the authoritative source modules without recalculation.

### 2. Controlled Actor & Event Model
The audit model distinguishes distinct actors:
- `AI`: Advisory root-cause analyses, investigations, and recovery strategy proposals (Modules 6, 7, 8).
- `POLICY_ENGINE`: Deterministic guardrail evaluations, limit clampings, and execution authorizations (Module 9).
- `RECOVERY_ENGINE`: Autonomous simulation executor and retry attempts (Module 10).
- `SYSTEM`: Background monitoring and detection engines (Module 3, 4).
- `HUMAN_OPERATOR` / `USER`: Operator commands, human reviews, manual approvals, and incident resolution.

Controlled event categories:
- `DETECTION`, `FINANCIAL`, `DIAGNOSIS`, `INVESTIGATION`, `STRATEGY`, `POLICY`, `EXECUTION`, `SYSTEM`.

### 3. Cryptographic Tamper-Evidence (SHA-256 Hash Chain)
To ensure decision history integrity, each event is cryptographically linked to the preceding event for that incident:
$$H_0 = \text{SHA-256}(\text{"GENESIS"} \mid \text{eventId} \mid \text{incidentId} \mid \text{eventType} \mid \text{timestamp} \mid \text{actor} \mid \text{summary} \mid \text{metadata})$$
$$H_n = \text{SHA-256}(H_{n-1} \mid \text{eventId} \mid \text{incidentId} \mid \text{eventType} \mid \text{timestamp} \mid \text{actor} \mid \text{summary} \mid \text{metadata})$$

If an audit record payload is tampered with or an event is deleted/inserted out of sequence, the verification engine (`GET /api/v1/incidents/{id}/audit/verify`) detects the discrepancy and points to the broken event ID.

### 4. Database Schema (`audit_events` table)
- `id` (Primary Key, sequential ID)
- `event_id` (Unique UUID string e.g. `AUD-...`)
- `incident_id`, `execution_id`, `strategy_id`, `policy_evaluation_id` (Foreign entity references)
- `event_type` (`AuditEventType` enum)
- `event_category` (`AuditEventCategory` enum)
- `actor_type` (`AuditActorType` enum)
- `actor_id` (String identifier e.g. `POLICY_V1`, `Gemini-1.5-Pro`)
- `severity` (`INFO`, `WARN`, `CRITICAL`), `status` (`SUCCESS`, `FAILURE`, `BLOCKED`, `INFO`)
- `summary`, `reason`
- `evidence_json`, `metadata_json` (Text payloads)
- `timestamp` (Instant)
- `previous_event_hash`, `event_hash` (SHA-256 hex strings)

### 5. Endpoints
- `GET /api/v1/incidents/{incidentId}/audit`: Retrieves full chronological audit timeline with decision chain summary and verification badge.
- `GET /api/v1/incidents/{incidentId}/audit/verify`: Verifies SHA-256 hash-chain integrity.
- `GET /api/v1/recovery-executions/{executionId}/audit`: Retrieves execution-specific timeline.

## Future layers (not implemented yet)

These packages exist as placeholders (`package-info.java`) so future modules slot in cleanly:

- **`safety/`** (Module 12 - Next) — safety and failure scenarios benchmarking.
- **`evaluation/`** (Module 13) — accuracy, recovery rate, and benchmark scoring.

See `development-roadmap.md` for the full module sequence.
