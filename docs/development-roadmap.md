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
| 8 | Recovery Strategy Engine | **Done** | Formulates bounded recovery strategy proposals with explicit stopping conditions and bounded financial estimates. |
| 9 | Policy / Guardrail Engine | **Done** | Deterministic validation gate that evaluates recovery recommendations against 12 hard safety policies with zero AI calls. |
| 10 | Recovery Execution Simulator | **Done** | Bounded recovery execution engine simulating retries obeying Module 9 policy limits, calculating actual recovered revenue with zero AI calls and circuit breakers. |
| 11 | Audit Trail | **Done** | Complete chronological decision history and tamper-evident SHA-256 hash-chain audit layer across Modules 0–10. |
| 12 | Safety & Failure Scenarios | Planned | Deliberate failure-mode testing: bad AI recommendations, policy rejections, escalation paths. |
| 13 | Evaluation & Benchmarking | Planned | Measurable evaluation of detection accuracy, recovery effectiveness, and system behavior over time. |
| 14 | Final Command Center | Planned | Consolidated operational view surfacing AI diagnosis, policy gates, recovery simulator, and audit trail. |
| 15 | Submission Package | Planned | Final packaging, documentation pass, and demo preparation. |

## Current state (Module 11)

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
Added in Module 8:

- `RecoveryStrategy` entity and repository (`recovery_strategies` table) recording generated recovery strategy proposals, confidence metrics, rationale, authoritative anchors, bounded financial estimates, stopping conditions, and alternative strategies.
- Controlled strategy enums: `RecoveryStrategyType` (`IMMEDIATE_RETRY`, `DELAYED_RETRY`, `ALTERNATE_PAYMENT_METHOD`, `RETRY_WITH_BACKOFF`, `MONITOR_ONLY`, `HUMAN_REVIEW`, `NO_ACTION`), `StrategyStatus` (`PROPOSED`, `REJECTED`, `SUPERSEDED`, `FAILED`, `INVALID_RESPONSE`), and `StrategySource` (`DETERMINISTIC_RULES`, `AI_ASSISTED`, `FALLBACK`).
- `StoppingConditionsDto` defining concrete bounded stopping criteria: max retry attempts, retry window in minutes, backoff interval, stop on success, stop on incident resolution, failure rate abort thresholds, and escalation limits.
- `AlternativeStrategyDto` and `RecoveryStrategyResponse` representing structured strategy proposals.
- `AiProvider.generateRecoveryStrategyJson` interface extension implemented in `MockAiProvider` and `GeminiAiProvider` with "DATA, NOT INSTRUCTIONS" prompt injection defense.
- `RecoveryStrategyService` orchestrating authoritative financial impact retrieval, deterministic evidence evaluation, AI strategy synthesis with fallback, strict range validation, financial bounding (estimated recovery <= authoritative recoverable), human approval guardrails, proposal superseding, and persistence.
- `RecoveryStrategyController` exposing `POST /api/v1/incidents/{incidentId}/recovery-strategy` and `GET /api/v1/incidents/{incidentId}/recovery-strategy`.
- Frontend `RecoveryStrategyPanel` on `/incidents/[id]` displaying the recommended strategy badge, confidence and risk metrics, expected recovery %, bounded financial estimates, stopping conditions checklist, human approval recommendation, alternative strategies comparison, and policy gate disclaimer.
- "Recovery Strategy" quick-action link added to active incident hero on `/dashboard`.
Added in Module 9:

- `policy/` package: `PolicyDecision` (`APPROVED`, `APPROVED_WITH_LIMITS`, `HUMAN_REVIEW`, `REJECTED`), `PolicyCheckResult` (`PASS`, `WARN`, `FAIL`), and `PolicyCheckSeverity` (`CRITICAL`, `MAJOR`, `MINOR`, `INFO`).
- `PolicyEvaluation` entity and repository (`policy_evaluations` table) recording complete deterministic policy decisions, versioned audit metadata (`evaluatorVersion = "POLICY_V1"`), execution authorization status (`executionAllowed`), human approval mandates, deterministic policy scores (0-100), applied parameter clampings, violations, warnings, and structured policy check results.
- `PolicyCheckDto`, `AppliedLimitDto`, and `PolicyEvaluationResponse` representing structured policy evaluation outcomes.
- `PolicyEvaluationService` executing 12 deterministic, testable safety policies with **ZERO LLM calls**:
  1. `POLICY-001`: Controlled Strategy Validation (only approved recovery strategy enums permitted)
  2. `POLICY-002`: Stopping Conditions Completeness (stop on success / resolved mandatory for retries)
  3. `POLICY-003`: Deterministic Retry Limits & Parameter Clamping (max 3 retry attempts, max 60 min window, min 10s backoff)
  4. `POLICY-004`: Risk Level Guardrail (CRITICAL blocks autonomous execution, HIGH requires human sign-off)
  5. `POLICY-005`: Diagnosis Confidence Guardrail (< 0.70 requires human sign-off, < 0.85 requires bounded limits)
  6. `POLICY-006`: Merchant Degradation Protection (blocks automated retries against degraded merchant APIs)
  7. `POLICY-007`: Alternate Payment Method Guardrail (mandates human authorization for checkout rerouting)
  8. `POLICY-008`: Financial Safety Bounds (estimated recovery <= Module 4 authoritative recoverable revenue)
  9. `POLICY-009`: Incident Resolution Guardrail (blocks recovery against RESOLVED incidents)
  10. `POLICY-010`: Strategy Lifecycle Guardrail (only active PROPOSED strategies eligible for evaluation)
  11. `POLICY-011`: Strategy Freshness Guardrail (strategies > 30 minutes require re-evaluation or human review)
  12. `POLICY-012`: Telemetry Evidence Integrity (deterministic validation of underlying telemetry)
- `PolicyEvaluationController` exposing `POST /api/v1/incidents/{incidentId}/policy-evaluate` and `GET /api/v1/incidents/{incidentId}/policy-evaluation`.
- Frontend `PolicyGatePanel` on `/incidents/[id]` displaying decision badge, execution permitted/blocked status indicator, metric cards, enforced parameter clampings breakdown, violations/warnings alerts, policy checks table, and prominent policy authority disclaimer.
- "Policy Gate" quick-action button in `ActiveIncidentHero` on `/dashboard`.
- Module progress indicator updated in `ModuleStatus.tsx`.
- Comprehensive unit and integration test suite (16 tests, 100% passing) verifying all policy decisions, limit clampings, merchant protections, financial bounds, and stale/resolved incident rejections.

Added in Module 10:

- `recovery/` package: `ExecutionStatus` (`REQUESTED`, `BLOCKED`, `RUNNING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `STOPPED`, `FAILED`), `RecoveryOutcome` (`RECOVERED`, `FAILED`, `SKIPPED`, `ALREADY_SUCCESSFUL`, `STOPPED_BY_LIMIT`, `STOPPED_BY_CIRCUIT_BREAKER`), and `StoppingReason` (`COMPLETED_ALL_ELIGIBLE`, `MAX_ATTEMPTS_REACHED`, `RETRY_WINDOW_EXCEEDED`, `CIRCUIT_BREAKER_FAILURE_RATE`, `INCIDENT_RESOLVED`, `HUMAN_APPROVAL_REQUIRED`, `POLICY_BLOCKED`, `NO_ACTION_REQUIRED`).
- `RecoveryExecution` JPA entity & repository (`recovery_executions` table) recording complete execution runs, status, transaction counts (eligible, attempted, successful, failed, skipped), authoritative financial anchors, actual recovered amount, enforced limits, simulation version, and stopping reasons.
- `RecoveryExecutionAttempt` JPA entity & repository (`recovery_execution_attempts` table) capturing transaction-level execution audit trail with attempt number, strategy type, timestamp, amount, outcome, and failure reason.
- `RecoveryExecutionAttemptDto`, `RecoveryMetricsDto`, and `RecoveryExecutionResponse` representing structured execution outputs.
- `RecoveryExecutionService` implementing bounded recovery simulation with **ZERO LLM calls**:
  1. Mandatory Policy Gate pre-execution check: independently verifies latest Module 9 `policyDecision` and `executionAllowed == true`.
  2. Enforced Policy Limits: clamps attempt limits, retry windows, and backoffs to Module 9 values.
  3. Deterministic synthetic payment selection from affected segment.
  4. Deterministic outcome simulation based on payment method, bank, and error taxonomy.
  5. Authoritative financial cap: guaranteed $\text{recoveredAmount} \le \text{authoritativeRecoverableRevenue}$.
  6. Failure-rate circuit breaker: halts execution with `STOPPED` if failure rate exceeds 50%.
  7. Idempotency guards: prevents duplicate executions for the same incident/strategy state.
- `RecoveryExecutionController` exposing `POST /api/v1/incidents/{incidentId}/recovery-execute`, `GET /api/v1/incidents/{incidentId}/recovery-execution`, and `GET /api/v1/recovery-executions/{executionId}`.
- Frontend `RecoveryExecutionPanel` on `/incidents/[id]` displaying Policy Gate authorization status badge, 4-Way KPI Comparison Card Grid (Revenue at Risk, Authoritative Recoverable, AI Estimate, Actual Simulated Outcome), stopping condition banner, secondary metrics bar, enforced policy limits, and transaction-level synthetic attempt timeline table.
- "Execute Recovery" action button added to active incident hero on `/dashboard`.
- Module progress indicator updated in `ModuleStatus.tsx` (Module 10: "built", Module 11: "queued").
- Comprehensive unit and integration test suite (15 tests in `RecoveryExecutionServiceTest` and `RecoveryExecutionControllerIntegrationTest`, 100% passing) verifying approved execution, limit clamping, policy rejections, circuit breakers, financial bounds, and idempotency.

Added in Module 11:

- `audit/` package: `AuditEventType` (controlled lifecycle event types: detection, diagnosis, investigation, strategy, policy, recovery execution, attempts, and completion), `AuditEventCategory` (`DETECTION`, `FINANCIAL`, `DIAGNOSIS`, `INVESTIGATION`, `STRATEGY`, `POLICY`, `EXECUTION`, `SYSTEM`), `AuditActorType` (`AI`, `POLICY_ENGINE`, `RECOVERY_ENGINE`, `SYSTEM`, `HUMAN_OPERATOR`, `USER`), `AuditSeverity`, and `AuditStatus`.
- `AuditEvent` JPA entity & repository (`audit_events` table) recording complete immutable-style audit records with incident ID, execution ID, strategy ID, policy evaluation ID, actor metadata, summary, failure reasons, structured evidence JSON, structured metadata JSON, timestamp, and SHA-256 hash chaining pointers (`previousEventHash`, `eventHash`).
- `AuditEventDto`, `AuditTimelineResponse`, and `AuditVerificationResponse` representing queryable timeline representations and cryptographic verification results.
- `AuditEventService` implementing tamper-evident audit recording with **ZERO LLM calls**:
  1. Automated lifecycle hooks across Modules 3 (detection/resolution), 4 (financial calculations), 6 (AI diagnosis request/complete/fail), 7 (investigation request/complete/fail), 8 (strategy proposal), 9 (policy approvals/rejections), and 10 (recovery execution requests, start, attempts, completion, circuit-breaker stops, and idempotent reuses).
  2. SHA-256 cryptographic hash-chaining linking every event per incident sequentially.
  3. Cryptographic integrity verification engine: detects broken previous-event pointers or payload tampering.
  4. Decision chain aggregator: summarizes overall progression (Detected → Diagnosed → Investigated → Strategy Proposed → Policy Approved → Recovery Executed → ₹ Recovered).
  5. Isolated non-blocking error handling: guarantees audit recording never disrupts core incident response operations.
- `AuditController` exposing REST APIs:
  - `GET /api/v1/incidents/{incidentId}/audit` (chronological incident audit timeline)
  - `GET /api/v1/incidents/{incidentId}/audit/verify` (tamper-evident hash chain verification)
  - `GET /api/v1/recovery-executions/{executionId}/audit` (execution-specific attempt timeline)
- Frontend `AuditTimeline` on `/incidents/[id]` featuring Decision Chain progression bar, SHA-256 cryptographic integrity verification badge, category filtering, clear visual actor distinction badges (AI vs Policy vs Execution vs System vs Human), expandable metadata inspector, and SHA-256 hash previews.
- "Audit Trail" quick-action link in `ActiveIncidentHero` on `/dashboard`.
- Module progress indicator updated in `ModuleStatus.tsx` (Module 11: "built", Module 12: "queued").
- Comprehensive unit and integration test suite (14 tests in `AuditEventServiceTest` and `AuditControllerIntegrationTest`, 100% passing) verifying all lifecycle hooks, chronological timelines, execution filtering, hash chaining, tampering detection, and read-only non-mutating safety.

Not implemented (by design, not oversight):

- Authentication of any kind.
- Safety & failure scenario benchmark runner (Module 12 - Next).
- Evaluation & Benchmarking (Module 13).
- Final Command Center (Module 14).
- Submission package (Module 15).

## Working agreement for future modules

- Keep the monolith. No new services, no message brokers, no Docker unless a
  future module genuinely requires it.
- Business logic stays in `service/`, never in `controller/`.
- New domain errors extend `SentinelException` so they flow through the
  existing `GlobalExceptionHandler` automatically.
- Every module ships as a complete project ZIP containing all previous work
  plus the new module — never a diff or partial file set.
