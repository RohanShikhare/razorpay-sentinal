# Razorpay Sentinel — Visual Demo & Screenshot Guide

This guide documents the key visual screens of Razorpay Sentinel and describes the recommended screenshots for hackathon submission reviewers.

---

## 1. Flagship Command Center (`/command-center`)
* **Visual Elements**:
  - **Subsystem Telemetry Header**: Live green badges for AI Advisory, Policy Gate (12 Rules), Recovery Simulator, and Cryptographic SHA-256 Audit Chain.
  - **1-Click Guided Demo Runner**: Active step indicator tracking 9-stage incident progression.
  - **Financial Impact Hero**: 4-quadrant cards distinguishing Revenue at Risk (Exposure) $\ne$ Recoverable Revenue $\ne$ Revenue Recovered $\ne$ Unrecovered / Protected Revenue.
  - **Active Incident Hero**: Red/Amber warning banner showing failure rate surge, affected bank/method, and error codes.
  - **8-Stage Decision Pipeline**: Interactive horizontal stepper showing completion checkmarks across the entire response lifecycle.
  - **6-Card Control Room Grid**: AI Advisory card, Root-Cause Investigation hypothesis card, Bounded Strategy card, 12-Rule Policy Gate checklist, Recovery Simulator stats, and SHA-256 Audit verification badge.

---

## 2. Evaluation & Benchmarking Console (`/evaluation`)
* **Visual Elements**:
  - **Hero KPI Cards**: Overall Sentinel Score (`91.51 / 100`), Sentinel Recovered Revenue (`₹77,350`), Recovery Rate (`68.8%`), Uplift vs Naive Retry (`+40.0%`), and Safety Score (`100/100`).
  - **Benchmark Controls**: Scenario Count slider, Deterministic Seed input (`42`), Compare Baselines toggle, and `Run Evaluation Benchmark` button.
  - **Baseline Comparison Section**: Visual bar charts comparing Baseline A (₹0, 0%) vs Baseline B (Naive Retry, ₹55,250) vs Sentinel Policy-Governed Recovery (₹77,350).
  - **4-Quadrant Analytical Grid**: Financial Effectiveness, Detection & Latency, Diagnosis & Strategy, Recovery & Safety.
  - **Per-Incident Breakdown Drawer**: Expandable incident inspector comparing ground truth with actual AI diagnosis and policy limits.

---

## 3. Safety & Failure Resilience Lab (`/safety`)
* **Visual Elements**:
  - **Safety Scoreboard**: `12/12 Scenarios Passed`, `15/15 Invariants Validated`, `0 Policy Bypasses`.
  - **Scenario Catalog Tabs**: AI & Parsing Failures, Policy & Risk Failures, Execution & System Failures, Audit & Tamper Failures.
  - **Scenario Inspector**: Side-by-side display of Expected vs Observed behavior, policy gate blocking proof, and SHA-256 audit tamper alerts.

---

## 4. Real-Time Monitoring & Anomaly Detection (`/monitoring`)
* **Visual Elements**:
  - **Failure Rate Surge Chart**: Recharts line chart comparing baseline failure rate vs current window surge.
  - **Multidimensional Breakdown Tables**: Failure rate deltas across Bank (HDFC, ICICI, SBI, Axis, Kotak), Payment Method (UPI, Card, NetBanking), and Error Codes.
  - **Active Incidents Table**: Priority-ranked active incident cards with direct links to deep incident investigation (`/incidents/[id]`).

---

## 5. Incident Deep Dive (`/incidents/[id]`)
* **Visual Elements**:
  - **Root-Cause Hypothesis Matrix**: Corroborating vs Counter-indicative evidence bars.
  - **Policy Safety Checklist**: Interactive 12-rule pass/fail indicators with applied limits.
  - **Cryptographic Audit Event Stream**: Chronological SHA-256 hash-chain timeline showing genesis block through verified recovery.
