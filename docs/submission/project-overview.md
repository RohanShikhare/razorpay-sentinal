# Razorpay Sentinel — Project Overview

**AI Payment Incident Commander**  
*Razorpay AI Buildathon — Track 3: AI Revenue Recovery*

---

## 1. Executive Summary

Payment failures cost online businesses billions annually in abandoned checkouts, broken consumer trust, and lost merchant revenue. While transient network hiccups can occasionally be resolved by blind retries, real payment incidents—such as bank gateway outages, acquirer latency spikes, or merchant credential misconfigurations—cannot be solved by blind retrying. In fact, blind retries during an upstream bank outage degrade system health, burn through customer retry limits, amplify load on failing infrastructure, and often lead to duplicate charges or chargebacks.

**Razorpay Sentinel** is an AI-assisted, policy-governed **AI Payment Incident Commander**. It detects abnormal payment failure spikes, isolates the affected segment, calculates the exact **revenue at risk**, conducts an advisory **AI root-cause diagnosis**, formulates a bounded **recovery strategy**, subjects it to a **100% deterministic safety policy gate**, executes simulated recovery safely with circuit breakers, and verifies every decision via a **tamper-evident SHA-256 cryptographic audit trail**.

---

## 2. Track 3 Alignment: AI Revenue Recovery

| Track 3 Requirement | Razorpay Sentinel Implementation |
|---|---|
| **Revenue-at-Risk Quantification** | The Financial Impact Engine calculates Total Revenue at Risk (exposure) and caps recovery at empirical Recoverability Tiers. |
| **Intelligent Root-Cause Diagnosis** | The Advisory AI Diagnostic Agent and Evidence Investigation Engine analyze factual telemetry to isolate bank gateway downtime vs merchant configuration issues. |
| **Bounded Recovery Interventions** | The Recovery Strategy Engine proposes bounded actions (`DELAYED_RETRY`, `RETRY_WITH_BACKOFF`, `ALTERNATE_PAYMENT_METHOD`, `HUMAN_REVIEW`) with explicit stopping conditions and backoff windows. |
| **Hard Safety & Guardrails** | The Deterministic Policy Gate evaluates 12 hard safety policies with zero AI calls, guaranteeing fail-closed safety. |
| **Measurable Recovered Revenue** | The Bounded Recovery Simulator executes retries within policy limits, enforces circuit breakers, and records actual simulated recovered revenue ($\text{₹}$). |
| **Cryptographic Accountability** | The SHA-256 hash-chained audit trail cryptographically proves every proposal, policy decision, retry attempt, and recovered rupee. |
| **Objective Benchmarking** | The Evaluation Engine demonstrates a **+40.0% uplift** over Naive Retry with 100% policy compliance. |

---

## 3. Core Architectural Principle

$$\Large \textbf{AI Proposes} \longrightarrow \textbf{Policy Decides} \longrightarrow \textbf{Recovery Executes} \longrightarrow \textbf{Audit Proves}$$

1. **AI is strictly advisory**: Generates hypotheses and bounded strategy proposals based on deterministic evidence fact sheets. AI never possesses direct execution authority.
2. **Policy is 100% deterministic**: 12 hard rules evaluate confidence, financial exposure, risk level, merchant degradation, and retry limits without LLM involvement.
3. **Execution is bounded & protected**: Retries are simulated under strict policy limits, monitored by a 50% failure circuit breaker, protected by idempotency keys, and capped by authoritative recoverable revenue.
4. **Audit is tamper-evident**: Every decision and transaction outcome is linked sequentially in a cryptographic SHA-256 hash-chain.

---

## 4. Key Financial Hierarchy Invariant

$$\Large \text{Actual Recovered Revenue} \le \text{Authoritative Recoverable Revenue} \le \text{Total Revenue at Risk}$$

- **Total Revenue at Risk (Exposure)**: Total rupee volume of all failed transactions during the incident window.
- **Authoritative Recoverable Revenue (Ceiling)**: Bounded recovery opportunity calculated by classifying failed transactions into empirical recoverability tiers.
- **Actual Recovered Revenue (Outcome)**: Realized rupee recovery measured after policy-governed simulated execution.

---

## 5. Measured Benchmark Highlights (EVAL_V1, Seed = 42, 4 Scenarios)

> [!NOTE]
> **Synthetic Benchmark Specification**: All results below are measured empirically from Sentinel's deterministic synthetic evaluation engine (`EVAL_V1`, seed = `42`, four scenarios) in a simulated test environment. They do NOT represent live Razorpay production customer data, real payment traffic, or actual monetary movement.

- **Overall Sentinel Score**: **91.51 / 100** (Sentinel benchmark criterion: $> 85.0$)
- **Sentinel Recovered Revenue**: **₹77,350.00** (out of ₹112,350.00 recoverable)
- **Baseline B (Naive Retry) Revenue**: **₹55,250.00**
- **Recovery Uplift vs Naive Retry**: **+40.0%**
- **Incident Detection Rate**: **100.0%** (0% False Positive Rate)
- **Root-Cause Diagnostic Accuracy**: **100.0%**
- **Policy Compliance Rate**: **100.0%**
- **Safety Invariant Violations**: **0 (Zero Bypasses, Zero Bound Violations)**
- **SHA-256 Audit Chain Integrity**: **100.0% Cryptographically Verified**
