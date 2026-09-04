package com.sentinelpulse.recovery;

/**
 * Transaction-level recovery outcome for an individual synthetic payment attempt.
 */
public enum RecoveryOutcome {
    RECOVERED,
    FAILED,
    SKIPPED,
    ALREADY_SUCCESSFUL,
    STOPPED_BY_LIMIT,
    STOPPED_BY_CIRCUIT_BREAKER
}
