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
| 5 | Revenue-at-Risk Engine | Superseded | First version delivered as part of Module 4 (see above); may be extended later. |
| 6 | Command Center Dashboard | Planned | Real dashboard UI: live incidents, charts (Recharts), and system state. |
| 7 | AI Diagnostic Agent | Planned | AI-driven root-cause analysis of a detected incident. |
| 8 | Root-Cause Investigation | Planned | Deeper diagnostic tooling and evidence gathering behind the AI agent's conclusions. |
| 9 | Recovery Strategy Engine | Planned | AI-generated, bounded recovery recommendations. |
| 10 | Policy / Guardrail Engine | Planned | Deterministic validation gate that recovery recommendations must pass before execution. |
| 11 | Recovery Execution Simulator | Planned | Simulated execution of an approved recovery strategy and resulting recovered revenue. |
| 12 | Audit Trail | Planned | Full, queryable record of every detection, decision, gate check, and recovery action. |
| 13 | Safety & Failure Scenarios | Planned | Deliberate failure-mode testing: bad AI recommendations, policy rejections, escalation paths. |
| 14 | Evaluation & Benchmarking | Planned | Measurable evaluation of detection accuracy, recovery effectiveness, and system behavior over time. |
| 15 | Final Command Center | Planned | Fully integrated dashboard tying together monitoring, AI, policy, recovery, and audit. |
| 16 | Submission Package | Planned | Final packaging, documentation pass, and demo preparation. |

## Current state (Module 3)

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

Not implemented (by design, not oversight):

- Authentication of any kind.
- AI root-cause diagnosis and recovery recommendation (this module's
  detector never guesses `rootCause` - see `docs/architecture.md`).
- Policy/guardrail validation.
- Recovery execution (simulated), audit trail, and evaluation.
- Incident lifecycle transitions beyond `DETECTED` (no resolution workflow yet).

## Working agreement for future modules

- Keep the monolith. No new services, no message brokers, no Docker unless a
  future module genuinely requires it.
- Business logic stays in `service/`, never in `controller/`.
- New domain errors extend `SentinelException` so they flow through the
  existing `GlobalExceptionHandler` automatically.
- Every module ships as a complete project ZIP containing all previous work
  plus the new module — never a diff or partial file set.
