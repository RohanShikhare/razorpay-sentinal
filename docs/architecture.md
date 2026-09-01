# Architecture

Razorpay Sentinel is a **monolith**, deliberately. Detection, diagnosis, policy
gating, recovery, and audit all live in one deployable backend so the
workflow can stay a single, traceable pipeline instead of a distributed
system with its own failure modes. This document describes the shape of that
monolith as of Module 0 (Project Foundation) and where each future layer will
sit.

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
- Recharts will be introduced when the Command Center Dashboard module needs
  charting; it is not a dependency yet.

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
- **Incident Detection Engine** (`monitoring/`, Module 4, not yet
  implemented) will *read* the same `payments` table - whether its
  abnormal state came from the simulator or real traffic - and apply
  threshold/window rules to decide whether what it sees constitutes an
  incident worth raising.

The simulator has no awareness of the detection engine and never will;
`SimulationRun`/`SimulationChange` are simulation records, not a detected
`Incident` entity. Keeping this boundary clean means Module 4 can be
validated against scenarios the simulator produces without either side
depending on the other's internals.

## Future layers (not implemented yet)

These packages exist as empty, documented placeholders (`package-info.java`)
so later modules slot in without restructuring the project:

- **`monitoring/`** — deterministic payment-failure detection: thresholds,
  windows, and anomaly rules applied to payment data. No AI here by design;
  detection needs to be predictable and testable. This will read from the
  `payments` table added in Module 1 via `PaymentRepository`/`PaymentSpecifications`,
  and will be exercised against the scenarios the Module 2 Incident Simulator
  can produce (see above).
- **`ai/`** — the AI layer. Root-cause analysis over a detected incident, and
  a bounded recovery recommendation. This is where an LLM call will live,
  isolated behind a service interface so the rest of the system doesn't
  depend on a specific provider.
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
