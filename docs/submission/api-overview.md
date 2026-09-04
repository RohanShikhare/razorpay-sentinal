# Razorpay Sentinel — REST API Specification

All backend endpoints are namespaced under `/api/v1` and return consistent JSON payloads.

---

## 1. System Health

* **`GET /api/v1/health`**
  * Description: Health check confirming Spring Boot backend and database connectivity.
  * Response: `{"status": "UP", "timestamp": "...", "service": "razorpay-sentinel"}`

---

## 2. Payment Data Engine

* **`POST /api/v1/payments/generate`**
  * Description: Generates realistic weighted synthetic payments.
  * Body: `{"count": 500, "hoursBack": 48, "seed": 42}`
* **`GET /api/v1/payments`**
  * Description: Filterable, paginated transaction listing.
  * Query Params: `status`, `bank`, `paymentMethod`, `errorCode`, `page`, `size`.
* **`GET /api/v1/payments/summary`**
  * Description: Aggregate database totals, failure rates, and breakdowns by bank, method, and error code.

---

## 3. Incident Simulation Engine

* **`POST /api/v1/simulations/run`**
  * Description: Deterministically injects a degradation scenario.
  * Body: `{"scenario": "BANK_X_UPI_DEGRADATION"}`
* **`POST /api/v1/simulations/reset`**
  * Description: Restores modified payment records to baseline.
* **`GET /api/v1/simulations/status`**
  * Description: Returns current active simulation state.

---

## 4. Anomaly Detection & Monitoring

* **`POST /api/v1/monitoring/detect`**
  * Description: Evaluates baseline vs current window, persists new or updated active incidents.
  * Body: `{"windowMinutes": 60}`
* **`GET /api/v1/incidents`**
  * Description: Retrieves all detected incident records.
* **`GET /api/v1/incidents/active`**
  * Description: Retrieves the highest-priority currently active incident.
* **`GET /api/v1/incidents/{id}`**
  * Description: Retrieves details for a specific incident by ID.

---

## 5. Financial Impact & Revenue-at-Risk Engine

* **`POST /api/v1/incidents/{id}/financial-impact/calculate`**
  * Description: Calculates authoritative revenue at risk, recoverable revenue, and recoverability tiers.
* **`GET /api/v1/incidents/{id}/financial-impact`**
  * Description: Retrieves existing financial impact calculation for incident.

---

## 6. Advisory AI Diagnostic Agent

* **`POST /api/v1/incidents/{id}/diagnosis`**
  * Description: Runs advisory AI diagnosis on structured factual telemetry.
* **`GET /api/v1/incidents/{id}/diagnosis`**
  * Description: Retrieves cached AI diagnosis record.

---

## 7. Competing Root-Cause Investigation Engine

* **`POST /api/v1/incidents/{id}/investigation`**
  * Description: Evaluates competing hypotheses against observed telemetry.
* **`GET /api/v1/incidents/{id}/investigation`**
  * Description: Retrieves investigation proof and evidence breakdown.

---

## 8. Recovery Strategy Formulation Engine

* **`POST /api/v1/incidents/{id}/recovery-strategy`**
  * Description: Formulates a bounded recovery strategy proposal with stopping conditions.
* **`GET /api/v1/incidents/{id}/recovery-strategy`**
  * Description: Retrieves proposed recovery strategy.

---

## 9. Deterministic Policy Gate

* **`POST /api/v1/incidents/{id}/policy-evaluation`**
  * Description: Evaluates proposal against 12 hard deterministic policy rules.
* **`GET /api/v1/incidents/{id}/policy-evaluation`**
  * Description: Retrieves policy decision (`APPROVED`, `APPROVED_WITH_LIMITS`, `HUMAN_REVIEW`, `REJECTED`).

---

## 10. Bounded Recovery Execution Simulator

* **`POST /api/v1/incidents/{id}/recovery-execution`**
  * Description: Simulates retry execution strictly obeying policy limits; records actual simulated recovered revenue.
* **`GET /api/v1/incidents/{id}/recovery-execution`**
  * Description: Retrieves execution outcome and transaction stats.

---

## 11. Cryptographic Audit Trail Engine

* **`GET /api/v1/incidents/{id}/audit/events`**
  * Description: Retrieves chronological audit event log.
* **`GET /api/v1/incidents/{id}/audit/verify`**
  * Description: Verifies SHA-256 cryptographic hash-chain integrity.

---

## 12. Safety & Resilience Lab

* **`GET /api/v1/safety/scenarios`**
  * Description: Returns catalog descriptors for all 12 controlled failure scenarios.
* **`POST /api/v1/safety/scenarios/{scenario}/run`**
  * Description: Runs an individual safety failure scenario.
* **`POST /api/v1/safety/scenarios/run-all`**
  * Description: Executes full 12-scenario safety test suite.

---

## 13. Evaluation & Benchmarking Engine

* **`GET /api/v1/evaluation/scenarios`**
  * Description: Returns benchmark scenario catalog with ground truths.
* **`POST /api/v1/evaluation/run`**
  * Description: Runs multi-scenario evaluation benchmark (`scenarioCount`, `seed`, `includeBaseline`).
* **`GET /api/v1/evaluation/latest`**
  * Description: Retrieves latest evaluation run.
* **`GET /api/v1/evaluation/runs/{runId}`**
  * Description: Retrieves evaluation run by ID.
* **`GET /api/v1/evaluation/runs/{runId}/incidents`**
  * Description: Retrieves granular per-incident benchmark metrics.
