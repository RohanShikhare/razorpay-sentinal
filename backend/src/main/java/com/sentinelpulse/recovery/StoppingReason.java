package com.sentinelpulse.recovery;

/**
 * Concrete stopping reason for why a recovery execution run finished or halted.
 */
public enum StoppingReason {
    COMPLETED_ALL_ELIGIBLE,
    MAX_ATTEMPTS_REACHED,
    RETRY_WINDOW_EXCEEDED,
    CIRCUIT_BREAKER_FAILURE_RATE,
    INCIDENT_RESOLVED,
    HUMAN_APPROVAL_REQUIRED,
    POLICY_BLOCKED,
    NO_ACTION_REQUIRED
}
