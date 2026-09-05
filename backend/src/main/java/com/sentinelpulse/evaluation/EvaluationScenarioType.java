package com.sentinelpulse.evaluation;

/**
 * Deterministic scenario types evaluated by the Evaluation & Benchmarking Engine.
 */
public enum EvaluationScenarioType {

    /**
     * Primary positive scenario: Spiked failure rate on Bank X UPI traffic dominated by transient BANK_TIMEOUT.
     * Expects detection = true, root cause = Bank X UPI timeout, safe delayed retry strategy, policy approved,
     * high recoverable revenue.
     */
    BANK_X_UPI_DEGRADATION,

    /**
     * Protection scenario: Isolated failure rate spike for a specific merchant with authentication or credential errors.
     * Expects detection = true, root cause = Merchant specific, policy gate restricts automated recovery
     * (HUMAN_REVIEW / REJECTED), zero automated retry execution.
     */
    MERCHANT_SPECIFIC_DEGRADATION,

    /**
     * Control negative scenario: Diffuse, non-concentrated baseline noise across banks and payment methods.
     * Expects no severe incident (detection = false or noise level), tests false positive avoidance,
     * zero unnecessary interventions.
     */
    NORMAL_RANDOM_FAILURES,

    /**
     * Multi-bank transient gateway timeout scenario: Elevated timeout errors across multiple banks/rails.
     * Expects detection = true, transient error recovery, policy approved with bounded retry limits.
     */
    GATEWAY_TIMEOUT_DEGRADATION
}
