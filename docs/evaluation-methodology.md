# Evaluation & Benchmarking Methodology

## Razorpay Sentinel — Track 3 Revenue Recovery Benchmark

---

## 1. Executive Summary & Evaluation Purpose

The **Evaluation & Benchmarking Engine** provides a deterministic, rigorous, reproducible framework to answer the core question of the Razorpay AI Revenue Recovery track:

> **"How much revenue did Sentinel recover, how efficiently did it recover it, how accurately did it diagnose incidents, and did it remain strictly safe while doing so?"**

> [!NOTE]
> **Synthetic Benchmark Distinction**: All benchmark results described in this document are generated using deterministic synthetic payment datasets (default seed = 42) in a controlled test environment. They do NOT represent live production Razorpay customer data or real monetary transactions.

Rather than relying on qualitative assertions or ad-hoc demos, the Evaluation Engine subjects Sentinel to controlled synthetic payment scenarios with known **ground truth**, comparing Sentinel against two baseline models (**Baseline A: No Recovery / Status Quo** and **Baseline B: Naive Retry**).

---

## 2. Evaluation Architecture

```text
Seeded Scenario Generator (Deterministic Pseudo-Randomness)
            ↓
Synthetic Payment Ingestion & Window Metric Spikes
            ↓
Incident Detection & Financial Metric Ingestion
            ↓
AI Diagnostic Agent & Root-Cause Investigation
            ↓
Recovery Strategy Generation
            ↓
Deterministic Policy Gate (Safety Checks)
            ↓
Recovery Execution Simulator
            ↓
Ground-Truth Comparator & Multi-Metric Aggregator (EVAL_V1)
            ↓
Persisted Evaluation Run & Interactive Benchmark Console (/evaluation)
```

---

## 3. Evaluation Scenario Catalog & Ground Truth

The evaluator defines 4 core scenario families with explicit ground truths:

| Scenario Type | Scenario Name | Ground Truth Description | Expected Root Cause Keywords | Expected Safe Strategies | Expected Policy Decision | Expected Execution Status |
|---|---|---|---|---|---|---|
| `BANK_X_UPI_DEGRADATION` | Bank X UPI Processing Outage | Bank X UPI gateway server error spike (`GATEWAY_ERROR`) | `bank x`, `upi`, `timeout`, `bank_timeout`, `switch`, `gateway` | `DELAYED_RETRY`, `RETRY_WITH_BACKOFF`, `ALTERNATE_PAYMENT_METHOD` | `APPROVED` | `COMPLETED_ALL_ELIGIBLE` |
| `MERCHANT_SPECIFIC_DEGRADATION` | Merchant Integration Error | Merchant integration key/payload degradation | `merchant`, `credential`, `auth`, `integration`, `config` | `HUMAN_REVIEW`, `MONITOR_ONLY`, `NO_ACTION` | `HUMAN_REVIEW` | `BLOCKED_POLICY_GATE` |
| `NORMAL_RANDOM_FAILURES` | Normal Random Baseline Failures | Low baseline noise without systematic pattern | `noise`, `random`, `baseline`, `diffuse`, `normal`, `nominal` | `MONITOR_ONLY`, `NO_ACTION`, `HUMAN_REVIEW` | `APPROVED` | `COMPLETED_ALL_ELIGIBLE` |
| `GATEWAY_TIMEOUT_DEGRADATION` | Upstream Gateway Latency & Timeout | Gateway timeout / network latency degradation | `timeout`, `gateway`, `network`, `latency`, `switch`, `rail` | `DELAYED_RETRY`, `RETRY_WITH_BACKOFF` | `APPROVED` | `COMPLETED_ALL_ELIGIBLE` |

---

## 4. Evaluated Dimensions & Mathematical Formulas

The evaluator measures **4 key operational dimensions** comprising 25+ metrics:

### Dimension 1: Financial Effectiveness
- **Total Revenue at Risk ($\text{TRR}$)**: Sum of transaction amounts at risk during detected incidents (exposure).
- **Total Recoverable Revenue ($\text{TRecR}$)**: Total revenue of failed transactions eligible for recovery actions (authoritative cap).
- **Sentinel Recovered Revenue ($\text{SRR}$)**: Total revenue successfully recovered by Sentinel via policy-governed actions.
- **Overall Recovery Rate ($\text{RR}$)**:
  $$\text{Recovery Rate (\%)} = \frac{\text{SRR}}{\max(1.0, \text{TRecR})} \times 100$$
- **Uplift vs Baseline A (No Recovery)**:
  $$\text{Uplift vs Baseline A (\%)} = 100.0\% \quad (\text{since Baseline A recovers ₹0})$$
- **Uplift vs Baseline B (Naive Retry)**:
  $$\text{Uplift vs Baseline B (\%)} = \frac{\text{SRR} - \text{NaiveRecovered}}{\max(1.0, \text{NaiveRecovered})} \times 100$$
- **Net Value Added**:
  $$\text{Net Value Added} = \text{SRR} - \text{Baseline A Recovered}$$
- **Cost / Benefit Ratio**:
  $$\text{Cost/Benefit Ratio} = \frac{\text{SRR}}{\max(1.0, \text{Operational Cost Estimate})}$$

### Dimension 2: Detection & Latency
- **Detection Rate ($\text{DR}$)**:
  $$\text{DR (\%)} = \frac{\text{Detected Incidents}}{\text{Total Scenarios}} \times 100$$
- **False Positive Rate ($\text{FPR}$)**: Percentage of normal traffic misclassified as critical incidents.
- **Time to Detect ($\text{TTD}$)**: Latency in milliseconds from synthetic data injection to incident record creation.

### Dimension 3: Diagnostic & Strategy Accuracy
- **Root-Cause Accuracy**:
  $$\text{Diagnosis Accuracy (\%)} = \frac{\sum \mathbb{I}(\text{Contains Expected Keywords})}{\text{Evaluated Incidents}} \times 100$$
- **Strategy Safety Precision**:
  $$\text{Safety Precision (\%)} = \frac{\sum \mathbb{I}(\text{Proposed Strategy} \in \text{Expected Safe Strategies})}{\text{Proposed Strategies}} \times 100$$
- **Policy Compliance Rate**:
  $$\text{Policy Compliance (\%)} = \frac{\sum \mathbb{I}(\text{Decision} == \text{Expected Policy Decision})}{\text{Decisions}} \times 100$$

### Dimension 4: Safety, Reliability & Invariants
- **Zero Policy Bypasses ($\text{Verified}$)**: Every execution simulator invocation is strictly gated by the Deterministic Policy Engine.
- **Zero Bound Violations ($\text{Verified}$)**: Max retry attempts, circuit breaker thresholds, and confidence limits are strictly obeyed.
- **Audit Trail Integrity ($\text{Integrity \%}$)**:
  $$\text{Audit Integrity (\%)} = \frac{\text{Incidents with Valid SHA-256 Chains}}{\text{Total Incidents}} \times 100$$
- **Safety Score**:
  $$\text{Safety Score} = 100 - (100 \times \text{Bypasses}) - (50 \times \text{Bound Violations}) - (50 \times \text{Audit Tamperings})$$

---

## 5. Baselines for Track 3 Comparison

Sentinel is objectively compared against two standard payment industry baselines:

1. **Baseline A: No Recovery (Status Quo)**
   - No automated intervention, retries, or rerouting.
   - Result: ₹0 recovered (0.0% recovery rate).
2. **Baseline B: Naive Retry (Blind 2x Execution)**
   - Blindly retries failed transactions without root-cause diagnosis, gateway switching, or merchant isolation.
   - For gateway outages or merchant key errors, naive retries fail repeatedly, recovering only transient failures (~30–50% recovery rate) while burning retry budgets.

---

## 6. Reproducibility & Evaluator Versioning

- **Evaluator Version**: `EVAL_V1`
- **Deterministic Seeding**: Uses `java.util.Random(seed)` with default seed `42` ensuring identical scenario parameters, timestamps, payment volumes, and transaction amounts across repeated runs.
- **Persistence**: All benchmark runs are persisted to the `evaluation_runs` database table with full serialized JSON summaries and per-incident breakdowns.

---

## 7. REST API Endpoints

- `GET /api/v1/evaluation/scenarios`: Returns available benchmark scenario descriptors and ground truths.
- `POST /api/v1/evaluation/run`: Executes a multi-scenario benchmark run (`scenarioCount`, `seed`, `includeBaseline`).
- `GET /api/v1/evaluation/latest`: Returns the most recently completed evaluation run.
- `GET /api/v1/evaluation/runs/{runId}`: Retrieves specific run by run ID.
- `GET /api/v1/evaluation/runs/{runId}/incidents`: Retrieves granular per-incident breakdown.
