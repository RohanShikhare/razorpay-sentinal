# Development Roadmap

Razorpay Sentinel is built module by module. Each module is additive — it
should not require restructuring work done in a previous module. This
document tracks the planned sequence and what "done" means for each one.

| # | Module | Status | Summary |
|---|--------|--------|---------|
| 1 | Project Foundation | **Done** | Monolith skeleton: Spring Boot backend with health endpoint, Next.js frontend, environment-based DB config, docs. |
| 2 | Payment Data Engine | **Done** | Synthetic payment generation, persistence, filtered/paginated query API, and an aggregate summary endpoint. |
| 3 | Incident Simulator | **Done** | Deterministic, reproducible payment-degradation scenarios (Bank X UPI, merchant-specific, normal noise) with reset. |
| 4 | Monitoring & Incident Detection | **Done** | Deterministic baseline-vs-current comparison, segment analysis, revenue-at-risk, confidence scoring, and Incident persistence with duplicate suppression. |
| 5 | Revenue-at-Risk Engine | **Done** | Rigorous financial-impact engine: recoverability classification, batch reconstruction, per-segment breakdowns, and structured evidence for the AI module. |
| 6 | AI Diagnostic Agent | **Done** | Advisory AI Diagnostic Agent analyzing structured deterministic telemetry, producing bounded recommendations with strict hallucination and policy guardrails. |
| 7 | Root-Cause Investigation | **Done** | Deep root-cause investigation systematically comparing competing hypotheses against deterministic telemetry, evaluating corroborating and counter-indicative evidence. |
| 8 | Recovery Strategy Engine | Planned | AI-generated, bounded recovery recommendations. |
| 9 | Policy / Guardrail Engine | Planned | Deterministic validation gate that recovery recommendations must pass before execution. |
| 10 | Recovery Execution Simulator | Planned | Simulated execution of an approved recovery strategy and resulting recovered revenue. |
| 11 | Audit Trail | Planned | Full, queryable record of every detection, decision, gate check, and recovery action. |
| 12 | Safety & Failure Scenarios | Planned | Deliberate failure-mode testing: bad AI recommendations, policy rejections, escalation paths. |
| 13 | Evaluation & Benchmarking | Planned | Measurable evaluation of detection accuracy, recovery effectiveness, and system behavior over time. |
| 14 | Final Command Center | Planned | Consolidated operational view surfacing AI diagnosis, policy gates, recovery simulator, and audit trail. |
| 15 | Submission Package | Planned | Final packaging, documentation pass, and demo preparation. |

## Current state (Module 7)

Implemented in Module 0:

- Monolithic Spring Boot backend (Java 17, Maven) with a layered
  `controller → service → repository` package structure.
- `GET /api/v1/health` health-check endpoint.
- Environment-variable based PostgreSQL configuration (no secrets committed,
  no database required to start the app yet).
- Global exception handling and validation foundation.
- CORS configured for local frontend development.
- Next.js + TypeScript + Tailwind frontend with a command-center-style
  homepage, navigation shell for future sections, and a live backend
  connectivity panel.
- `docs/architecture.md` and this roadmap.

Added in Module 1:

- `Payment` JPA entity (`entity/`) with `PaymentMethod`, `PaymentStatus`,
  and `ErrorCode` enums, plus a `Banks` constants holder.
- `PaymentRepository` with `JpaSpecificationExecutor` and `PaymentSpecifications`
  for composable filtering (status, payment method, bank, error code, time window).
- `PaymentGeneratorService` - realistic, weighted synthetic payment generation
  (configurable time window, seeded-random reproducibility).
- `PaymentService` - filtered/paginated queries and a database-aggregated summary.
- `PaymentController` - `POST /api/v1/payments/generate`, `GET /api/v1/payments`,
  `GET /api/v1/payments/summary`.
- A `/payments` page in the frontend: generate synthetic data, filter, and
  page through the results, backed by `lib/payments.ts`.
- Unit tests for generation distributions and an H2-backed integration test
  suite covering generation, persistence, filtering, pagination, and summary math.

Added in Module 2:

- `SimulationScenario` enum and lightweight `SimulationRun`/`SimulationChange`
  simulation records (deliberately *not* an "Incident" entity - see
  `docs/architecture.md` for why that distinction matters).
- `IncidentSimulationService` - deterministically degrades the synthetic
  dataset via three scenarios (`BANK_X_UPI_DEGRADATION`,
  `MERCHANT_SPECIFIC_DEGRADATION`, `NORMAL_RANDOM_FAILURES`), each
  reproducible and concentrated so later root-cause analysis has something
  real to find.
- `SimulationController` - `POST /api/v1/simulations/run`,
  `POST /api/v1/simulations/reset`, `GET /api/v1/simulations/status`.
- Reset support via recorded pre-simulation values (no dataset regeneration
  needed), and automatic reset-then-reapply if a simulation is already
  active, so repeated runs never compound.
- A `/simulator` page in the frontend: scenario selector, Simulate/Reset
  controls, and a HEALTHY / SIMULATION ACTIVE status panel. The `/payments`
  page now shows a small "simulation active" indicator when applicable.
- Integration tests covering the Bank X UPI concentration, the merchant
  scenario's isolation, the normal-noise scenario's lack of concentration,
  reset restoring the pre-simulation state, and idempotency under repeated runs.
- Refinement: simulated degradation is now concentrated in the most recent
  transactions rather than spread uniformly across the whole 48h dataset -
  both more realistic (a real incident affects recent traffic, not
  retroactively altered old transactions) and necessary for Module 4's
  time-window-based baseline/current comparison to actually see it. The
  `NORMAL_RANDOM_FAILURES` control scenario intentionally stays uniform, so
  it reads as constant background noise rather than a new event.

Added in Module 3:

- `Incident` entity plus `IncidentType`, `IncidentSeverity`, `IncidentStatus`
  enums. `rootCause` stays null - assigning one is a future AI module's job.
- `MonitoringProperties` - every threshold, window, and sample-size number
  the detector uses, bound from `sentinel.monitoring.*` in application.yml.
- `PaymentStatistics` and `SegmentAnalyzer` - pure, side-effect-free
  statistics helpers (bank / payment method / bank+method / merchant
  baseline-vs-current comparison).
- `IncidentDetectionService` - the deterministic detection engine: adaptive
  window widening (so a narrow window over a sparse dataset doesn't
  produce meaningless swings), segment-level detection, severity
  classification, a documented revenue-at-risk formula, a deterministic
  confidence score, and duplicate suppression (refreshes an existing
  active incident instead of creating a new row for the same condition).
- `MonitoringController` - `POST /api/v1/monitoring/detect`,
  `GET /api/v1/monitoring/summary`.
- `IncidentController` - `GET /api/v1/incidents` (paginated),
  `GET /api/v1/incidents/{id}` (with live-recomputed segment breakdowns).
- `/monitoring` (command center: health banner, metrics, failure-rate and
  segment charts via Recharts) and `/incidents` + `/incidents/[id]` pages
  in the frontend.
- Unit tests for the statistics/segment-analysis helpers, plus an H2-backed
  integration suite covering all three simulator scenarios, duplicate
  suppression, pagination, and the monitoring summary.

Added in Module 4:

- `financial/` package: `RecoverabilityLevel` enum, `RecoverabilityClassifier`
  (fixed, documented error-code → recoverability-level mapping), and
  `FinancialImpactService` - the orchestrating engine.
- `RecoverabilityProperties` - configurable numeric rate per recoverability
  level, bound from `sentinel.financial.recoverability-rates.*`.
- Affected-batch reconstruction directly from the `payments` table (no
  duplicated transaction storage), using each incident's own target
  bank/payment-method/merchant + start time as deterministic criteria.
- A documented recoverable/non-recoverable split on top of Module 3's
  existing revenue-at-risk figure - read, not recomputed, so it stays an
  explainable snapshot of "what was at risk when this was detected" even
  if the underlying payment data changes later.
- `GET /api/v1/incidents/{id}/financial-impact` and
  `/financial-impact/breakdown` (by bank / payment method / error code /
  merchant), plus `GET /api/v1/incidents/{id}/evidence` - structured,
  map-shaped evidence ready for the future AI investigation module to
  consume directly, without exposing it via an actual AI call.
- `GET /api/v1/monitoring/summary` extended with `estimatedRecoverableRevenue`
  and `affectedTransactionValue`, aggregated across active incidents.
- A financial-impact section (summary cards, a recoverable-vs-at-risk
  progress bar, and revenue-at-risk-by-segment charts) added to the
  incident detail page.
- Unit tests using hand-built, controlled-amount payments to verify the
  formulas exactly, plus integration tests tying together simulate →
  detect → financial-impact for the Bank X UPI and merchant scenarios.

Added in Module 5:

- `/dashboard` — the new Command Center route, the project's primary
  operations view. Consolidates data already exposed by Modules 1-4
  (`GET /api/v1/monitoring/summary`, `/incidents`, `/incidents/{id}/financial-impact`,
  `/payments/summary`) rather than introducing a parallel data path.
- `PaymentSummaryResponse`'s `byBank`/`byPaymentMethod` breakdown entries
  extended with `failedCount`, `failedValue`, and `failureRate`; `byErrorCode`
  extended with `value` and `percentageOfFailures` - a small, additive,
  reused-service extension (`PaymentRepository`/`PaymentService`, Module 1)
  rather than a new aggregation service, so the dashboard's bank/method/error-code
  breakdown tables work even in the healthy state, not just per-incident.
- `GET /api/v1/monitoring/summary` was already extended in Module 4;
  no further backend change was needed for the dashboard's top KPI cards.
- KPI cards (payment success rate vs. baseline, revenue at risk, estimated
  recoverable revenue, active incidents, affected payments), an active-incident
  hero card (reusing the same fields as the incident detail page - explicitly
  still shows "Root Cause: Not yet investigated"), a shared `IncidentsTable`
  component (used by both `/incidents` and `/dashboard`, avoiding a
  duplicate table implementation), bank/payment-method/error-code breakdown
  tables, and a quick-operations panel (simulate/detect/reset, reusing the
  same `ScenarioSelector` component and API functions the `/simulator` page
  already uses).
- Manual refresh plus 20-second auto-refresh polling, guarded against
  overlapping in-flight requests.
- Proper loading, error (with retry), and empty states throughout; the
  existing `/`, `/payments`, `/simulator`, `/monitoring`, and `/incidents`
  pages remain fully intact and reachable from navigation.

Added in Module 6:

- `ai/` package: `AiProvider` interface, `MockAiProvider` (deterministic scenario diagnoses for Bank X UPI, Merchant degradation, and Normal noise), `GeminiAiProvider` (REST client for live LLM), and `AiProviderFactory`.
- `ControlledAction` enum (`IMMEDIATE_RETRY`, `DELAYED_RETRY`, `ALTERNATE_PAYMENT_METHOD`, `MONITOR`, `HUMAN_REVIEW`, `NO_ACTION`) — bounded actions only, avoiding uncontrolled free text.
- `RiskLevel` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) and `DiagnosisStatus` (`REQUESTED`, `COMPLETED`, `FAILED`, `INVALID_RESPONSE`).
- `AiProperties` - configurable via `sentinel.ai.*` (`AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_TEMPERATURE`, `AI_TIMEOUT_MS`).
- `IncidentEvidenceService` - deterministic extraction and structuring of incident telemetry and Module 4 financial calculations.
- `AiDiagnosticService` - prompt synthesis with "DATA, NOT INSTRUCTIONS" prompt injection defense, strict JSON schema validation, range checks (confidence 0-1, recovery % 0-100), conservative human approval guardrails, diagnosis persistence to `incident_diagnoses` table, and incident root cause updates.
- `AiDiagnosticController` - `POST /api/v1/incidents/{incidentId}/diagnose` (on-demand trigger) and `GET /api/v1/incidents/{incidentId}/diagnosis` (retrieve latest).
- Frontend `AiInvestigationPanel` on `/incidents/[id]` - "Investigate with AI" button, loading/analyzing state animation, root cause headline, confidence gauge, observed telemetry points, diagnostic reasoning, bounded action card, advisory recovery potential %, risk badge, human approval required gate, and prominent safety guardrail banner.
- Active incident quick-link updated on `/dashboard`.
Added in Module 7:

- `IncidentInvestigation` entity and repository (`incident_investigations` table) recording deep investigative outcomes, evaluated hypotheses, confidence metrics, and uncertainty disclaimers.
- `RootCauseHypothesis` DTO and `IncidentInvestigationResponse` representing ranked competing theories (`STRONGEST`, `LIKELY`, `UNLIKELY`, `REFUTED`, `INCONCLUSIVE`).
- `AiProvider.generateInvestigationJson` interface extension implemented across `MockAiProvider` (deterministic scenario hypotheses for Bank X UPI, Merchant degradation, and diffuse noise) and `GeminiAiProvider` (live LLM with "DATA, NOT INSTRUCTIONS" prompt injection defense).
- `IncidentInvestigationService` orchestrating deterministic evidence compilation, hypothesis formulation, strict schema validation, bounds checks, conservative human review flags (< 0.70 confidence), and database persistence.
- `IncidentInvestigationController` exposing `POST /api/v1/incidents/{incidentId}/investigate` and `GET /api/v1/incidents/{incidentId}/investigation`.
- Frontend `RootCauseInvestigationPanel` on `/incidents/[id]` displaying the strongest root cause, overall confidence meter, competing hypotheses comparison cards with supporting (✓) and contradicting (✕) evidence bullets, diagnostic deduction narrative, and acknowledged uncertainty disclosures.
- "Root-Cause Analysis" action link added to active incident hero on `/dashboard`.
- Comprehensive unit and integration test suite covering hypothesis rankings, scenario deductions, out-of-bounds metrics, malformed responses, and controller endpoints.

Not implemented (by design, not oversight):

- Authentication of any kind.
- Recovery Strategy Engine (Module 8).
- Policy/guardrail validation engine (Module 9).
- Recovery execution simulator (Module 10).
- Audit trail (Module 11).
- Incident lifecycle transitions beyond `DETECTED`/`INVESTIGATING` (no resolution workflow yet).

## Working agreement for future modules

- Keep the monolith. No new services, no message brokers, no Docker unless a
  future module genuinely requires it.
- Business logic stays in `service/`, never in `controller/`.
- New domain errors extend `SentinelException` so they flow through the
  existing `GlobalExceptionHandler` automatically.
- Every module ships as a complete project ZIP containing all previous work
  plus the new module — never a diff or partial file set.
