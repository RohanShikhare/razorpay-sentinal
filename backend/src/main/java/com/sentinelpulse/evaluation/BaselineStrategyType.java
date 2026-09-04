package com.sentinelpulse.evaluation;

/**
 * Baseline strategies compared against Sentinel during evaluation.
 */
public enum BaselineStrategyType {

    /**
     * Baseline A — No Recovery: No intervention is performed (Recovered Revenue = ₹0).
     * Establishes the revenue-loss lower bound.
     */
    NO_RECOVERY,

    /**
     * Baseline B — Naive Retry: Retries every eligible failed transaction exactly once
     * without AI diagnosis, root-cause investigation, or policy guardrails.
     * Subject to actual transaction retry recoverability and capped by authoritative recoverable revenue.
     */
    NAIVE_RETRY
}
