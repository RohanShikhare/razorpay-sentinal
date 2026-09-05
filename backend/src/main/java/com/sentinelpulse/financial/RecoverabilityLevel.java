package com.sentinelpulse.financial;

/**
 * How plausible it is that a failed transaction's revenue can eventually be
 * recovered, based purely on its error code. This is an estimate used to
 * split revenue-at-risk into a recoverable/non-recoverable split - it is
 * NOT a prediction that recovery will actually happen, and no recovery
 * action is taken anywhere in this service.
 */
public enum RecoverabilityLevel {
    HIGH_RECOVERABILITY,
    MEDIUM_RECOVERABILITY,
    LOW_RECOVERABILITY,
    UNKNOWN
}
